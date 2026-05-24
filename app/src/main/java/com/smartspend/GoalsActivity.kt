package com.smartspend

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Goal
import com.smartspend.data.entity.SpendingGoal
import com.smartspend.data.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt

class GoalsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private var selectedImageUri: Uri? = null
    private var featuredGoal: Goal? = null
    private lateinit var goalsAdapter: GoalsAdapter

    // Image picker for creating a new goal
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            findViewById<ImageView>(R.id.ivNewGoalImage).setImageURI(it)
            findViewById<TextView>(R.id.tvUploadImage).text = getString(R.string.image_selected)
        }
    }

    // Featured image picker for updating existing featured goal
    private val featuredImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            findViewById<ImageView>(R.id.ivFeaturedGoalImage).setImageURI(it)
            featuredGoal?.let { goal ->
                lifecycleScope.launch {
                    val updatedGoal = goal.copy(imagePath = it.toString())
                    db.goalDao().update(updatedGoal)

                    // Sync updated image path to Firebase
                    FirebaseRepository().saveGoal(updatedGoal)
                    Log.d("GoalsActivity", "Goal image path synced to Firebase: ${updatedGoal.goalId}")

                    loadFeaturedGoal()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        // Linking Navigation Helper to the menu button ID from your XML
        NavigationHelper.setupMenu(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@GoalsActivity)
            }
        })

        setupRecyclerView()
        setupButtons()
        setupSpendingGoalsSection()
        loadAllGoals()
        loadFeaturedGoal()
    }

    private fun setupRecyclerView() {
        goalsAdapter = GoalsAdapter(emptyList()) { goal ->
            showUpdateGoalDialog(goal)
        }
        findViewById<RecyclerView>(R.id.rvAllGoals).apply {
            layoutManager = LinearLayoutManager(this@GoalsActivity)
            adapter = goalsAdapter
        }
    }

    private fun setupButtons() {
        // Featured image tap (Frame holding the image)
        findViewById<FrameLayout>(R.id.frameFeaturedImage).setOnClickListener {
            featuredImagePickerLauncher.launch("image/*")
        }

        // Image upload layout for new goal
        findViewById<LinearLayout>(R.id.layoutUploadImage).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Target date picker (TextInputEditText)
        findViewById<TextInputEditText>(R.id.etTargetDate).setOnClickListener {
            showDatePicker { date ->
                findViewById<TextInputEditText>(R.id.etTargetDate).setText(date)
            }
        }

        // Create goal button
        findViewById<MaterialButton>(R.id.btnCreateGoal).setOnClickListener {
            createGoal()
        }

        // Add savings button
        findViewById<MaterialButton>(R.id.btnAddSavings).setOnClickListener {
            showAddSavingsDialog()
        }

        // Update goal button
        findViewById<MaterialButton>(R.id.btnUpdateGoal).setOnClickListener {
            showSelectGoalToUpdateDialog()
        }
    }

    private fun loadFeaturedGoal() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            Log.d("GoalsActivity", "Loading featured goal for userId: $userId")
            val goal = db.goalDao().getFeaturedGoal(userId)
            featuredGoal = goal

            if (goal != null) {
                val percentage = if (goal.targetAmount > 0)
                    ((goal.currentAmount / goal.targetAmount) * 100).toFloat()
                else 0f

                if (percentage >= 100f) {
                    db.goalDao().markAsCompleted(goal.goalId)
                    showCongratulationsCard()
                    loadFeaturedGoal()
                    return@launch
                }

                runOnUiThread {
                    findViewById<TextView>(R.id.tvFeaturedGoalTitle).text = goal.goalName
                    findViewById<TextView>(R.id.tvFeaturedGoalDate).text =
                        getString(R.string.target_date_format, goal.targetDate)
                    findViewById<TextView>(R.id.tvFeaturedCurrentAmount).text =
                        getString(R.string.amount_format, goal.currentAmount)
                    findViewById<TextView>(R.id.tvFeaturedGoalAmount).text =
                        getString(R.string.amount_format, goal.targetAmount)
                    findViewById<TextView>(R.id.tvFeaturedGoalPercentage).text =
                        "${percentage.toInt()}%"
                    findViewById<ProgressBar>(R.id.progressFeaturedGoal).progress =
                        percentage.toInt()

                    // Custom PieChartView logic
                    val pieChart = findViewById<PieChartView>(R.id.pieChartFeatured)
                    val slices = listOf(
                        PieSlice("Progress", goal.currentAmount, "#6A11CB".toColorInt()),
                        PieSlice("Remaining", (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0), "#F0F0F0".toColorInt())
                    )
                    pieChart.setData(slices)

                    goal.imagePath?.let { path ->
                        findViewById<ImageView>(R.id.ivFeaturedGoalImage)
                            .setImageURI(path.toUri())
                    }
                }
            } else {
                runOnUiThread {
                    findViewById<TextView>(R.id.tvFeaturedGoalTitle).text =
                        getString(R.string.no_goals_yet)
                    findViewById<TextView>(R.id.tvFeaturedGoalDate).text = ""
                    findViewById<TextView>(R.id.tvFeaturedCurrentAmount).text = "R 0"
                    findViewById<TextView>(R.id.tvFeaturedGoalAmount).text = "R 0"
                    findViewById<TextView>(R.id.tvFeaturedGoalPercentage).text = "0%"
                    findViewById<ProgressBar>(R.id.progressFeaturedGoal).progress = 0
                    findViewById<PieChartView>(R.id.pieChartFeatured).setData(emptyList())
                }
            }
        }
    }

    private fun loadAllGoals() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            Log.d("GoalsActivity", "Loading all goals for userId: $userId")
            val goals = db.goalDao().getActiveGoals(userId)
            runOnUiThread {
                goalsAdapter.updateGoals(goals)
            }
        }
    }

    private fun createGoal() {
        val name = findViewById<TextInputEditText>(R.id.etGoalName).text.toString().trim()
        val amountStr = findViewById<TextInputEditText>(R.id.etGoalAmount).text.toString().trim()
        val date = findViewById<TextInputEditText>(R.id.etTargetDate).text.toString().trim()

        when {
            name.isEmpty() -> {
                Toast.makeText(this, "Enter goal name", Toast.LENGTH_SHORT).show()
                return
            }
            amountStr.isEmpty() -> {
                Toast.makeText(this, "Enter goal amount", Toast.LENGTH_SHORT).show()
                return
            }
            date.isEmpty() -> {
                Toast.makeText(this, "Enter target date", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            Log.d("GoalsActivity", "Creating goal for userId: $userId")
            val newGoal = Goal(
                userId = userId,
                goalName = name,
                targetAmount = amount,
                targetDate = date,
                imagePath = selectedImageUri?.toString()
            )
            val newId = db.goalDao().insert(newGoal)
            val savedGoal = newGoal.copy(goalId = newId.toInt())

            // Sync to Firebase with the real Room-generated ID
            FirebaseRepository().saveGoal(savedGoal)

            runOnUiThread {
                Toast.makeText(this@GoalsActivity, "Goal created", Toast.LENGTH_SHORT).show()
                // Reset Form
                findViewById<TextInputEditText>(R.id.etGoalName).text?.clear()
                findViewById<TextInputEditText>(R.id.etGoalAmount).text?.clear()
                findViewById<TextInputEditText>(R.id.etTargetDate).text?.clear()
                findViewById<ImageView>(R.id.ivNewGoalImage).setImageResource(android.R.drawable.ic_menu_camera)
                findViewById<TextView>(R.id.tvUploadImage).text = "Upload Goal Image"
                selectedImageUri = null
            }
            loadAllGoals()
            loadFeaturedGoal()
        }
    }

    private fun showAddSavingsDialog() {
        val goal = featuredGoal ?: run {
            Toast.makeText(this, "No goals yet", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Enter amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Savings to ${goal.goalName}")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    lifecycleScope.launch {
                        val newAmount = goal.currentAmount + amount
                        db.goalDao().updateCurrentAmount(goal.goalId, newAmount)
                        Log.d("GoalsActivity", "Savings added to '${goal.goalName}': +$amount → total $newAmount")

                        runOnUiThread {
                            Toast.makeText(this@GoalsActivity, "Savings added", Toast.LENGTH_SHORT).show()
                        }
                        loadFeaturedGoal()
                        loadAllGoals()
                    }
                } else {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSelectGoalToUpdateDialog() {
        lifecycleScope.launch {
            val goals = db.goalDao().getActiveGoals(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            if (goals.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this@GoalsActivity, "No goals yet", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val goalNames = goals.map { it.goalName }.toTypedArray()

            runOnUiThread {
                AlertDialog.Builder(this@GoalsActivity)
                    .setTitle("Select goal to update")
                    .setItems(goalNames) { _, index ->
                        showUpdateGoalDialog(goals[index])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showUpdateGoalDialog(goal: Goal) {
        val input = EditText(this).apply {
            hint = "Enter additional amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Update ${goal.goalName}")
            .setMessage("Current: R ${goal.currentAmount} / R ${goal.targetAmount}")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    lifecycleScope.launch {
                        val newAmount = goal.currentAmount + amount
                        db.goalDao().updateCurrentAmount(goal.goalId, newAmount)

                        // Sync updated currentAmount to Firebase
                        val updatedGoal = goal.copy(currentAmount = newAmount)
                        FirebaseRepository().saveGoal(updatedGoal)
                        Log.d("GoalsActivity", "Goal current amount synced to Firebase: $newAmount")

                        runOnUiThread {
                            Toast.makeText(this@GoalsActivity, "Goal updated", Toast.LENGTH_SHORT).show()
                        }
                        loadFeaturedGoal()
                        loadAllGoals()
                    }
                } else {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCongratulationsCard() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("🎉 Congratulations!")
                .setMessage("You've reached your goal: ${featuredGoal?.goalName ?: ""}")
                .setPositiveButton("Awesome!", null)
                .show()
        }
    }

    private fun setupSpendingGoalsSection() {
        val etMinSpend = findViewById<EditText>(R.id.etMinMonthlySpend)
        val etMaxSpend = findViewById<EditText>(R.id.etMaxMonthlySpend)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveSpendingGoals)

        btnSave.setOnClickListener {
            val minStr = etMinSpend.text.toString().trim()
            val maxStr = etMaxSpend.text.toString().trim()

            when {
                minStr.isEmpty() -> {
                    Toast.makeText(this, "Enter minimum monthly spend", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                maxStr.isEmpty() -> {
                    Toast.makeText(this, "Enter maximum monthly spend", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val min = minStr.toDoubleOrNull()
            val max = maxStr.toDoubleOrNull()

            when {
                min == null || min < 0 -> {
                    Toast.makeText(this, "Enter a valid minimum amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                max == null || max <= 0 -> {
                    Toast.makeText(this, "Enter a valid maximum amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                min >= max -> {
                    Toast.makeText(this, "Minimum must be less than maximum", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val month = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))

            lifecycleScope.launch {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("GoalsActivity", "Saving spending goal for userId: $userId")
                val spendingGoal = SpendingGoal(
                    userId = userId,
                    minMonthlySpend = min!!,
                    maxMonthlySpend = max!!,
                    month = month
                )
                val newId = db.spendingGoalDao().insert(spendingGoal)
                val savedGoal = spendingGoal.copy(id = newId.toInt())
                Log.d("GoalsActivity", "Spending goals saved: min=$min max=$max month=$month")

                // Sync to Firebase
                FirebaseRepository().saveSpendingGoal(savedGoal)

                runOnUiThread {
                    Toast.makeText(this@GoalsActivity, "Spending goals saved", Toast.LENGTH_SHORT).show()
                    etMinSpend.text?.clear()
                    etMaxSpend.text?.clear()
                }
            }
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = "$year-${(month + 1).toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                onDateSelected(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}