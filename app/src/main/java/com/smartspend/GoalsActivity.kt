package com.smartspend

import android.app.DatePickerDialog
import android.graphics.Color
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.File
import com.smartspend.data.PieSlice
import com.smartspend.PieChartView
import android.view.View

class GoalsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private var selectedImageUri: Uri? = null
    private var featuredGoal: Goal? = null
    private lateinit var goalsAdapter: GoalsAdapter

    // Layout View References
    private lateinit var tvGoalName: TextView
    private lateinit var tvGoalDate: TextView
    private lateinit var tvCurrentAmount: TextView
    private lateinit var tvTargetAmount: TextView
    private lateinit var progressBarGoal: ProgressBar
    private lateinit var ivGoalImage: ImageView

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
                    FirebaseRepository().saveGoal(updatedGoal)
                    Log.d("GoalsActivity", "Goal image path synced to Firebase: ${updatedGoal.goalId}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)
        val savedTheme = themePrefs.getString("theme_colour", "blue") ?: "blue"
        val themeRes = when (savedTheme) {
            "green"  -> R.style.Theme_SmartSpend_Green
            "purple" -> R.style.Theme_SmartSpend_Purple
            "orange" -> R.style.Theme_SmartSpend_Orange
            else     -> R.style.Theme_SmartSpend_Blue
        }
        setTheme(themeRes)
        setContentView(R.layout.activity_goals)

        // 🌟 FIX: Bind the layout references directly matching your XML definitions
        tvGoalName = findViewById(R.id.tvFeaturedGoalTitle)
        tvGoalDate = findViewById(R.id.tvFeaturedGoalDate)
        tvCurrentAmount = findViewById(R.id.tvFeaturedCurrentAmount)
        tvTargetAmount = findViewById(R.id.tvFeaturedGoalAmount)
        progressBarGoal = findViewById(R.id.progressFeaturedGoal) // Matched to line 197 in XML
        ivGoalImage = findViewById(R.id.ivFeaturedGoalImage)

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
        findViewById<FrameLayout>(R.id.frameFeaturedImage).setOnClickListener {
            featuredImagePickerLauncher.launch("image/*")
        }

        findViewById<LinearLayout>(R.id.layoutUploadImage).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        findViewById<TextInputEditText>(R.id.etTargetDate).setOnClickListener {
            showDatePicker { date ->
                findViewById<TextInputEditText>(R.id.etTargetDate).setText(date)
            }
        }

        findViewById<MaterialButton>(R.id.btnCreateGoal).setOnClickListener {
            createGoal()
        }

        findViewById<MaterialButton>(R.id.btnAddSavings).setOnClickListener {
            showAddSavingsDialog()
        }

        findViewById<MaterialButton>(R.id.btnUpdateGoal).setOnClickListener {
            showSelectGoalToUpdateDialog()
        }
    }

    private fun loadFeaturedGoal() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            db.goalDao().getFeaturedGoal(userId).collect { goal ->
                if (goal != null) {
                    featuredGoal = goal

                    // 1. DEFINE THESE VARIABLES FIRST so they are available below
                    val target = goal.targetAmount
                    val current = goal.currentAmount
                    val progressPercent = if (target > 0) ((current / target) * 100).toInt() else 0

                    // Now these variables are resolved
                    tvGoalName.text = goal.goalName
                    tvGoalDate.text = "Target: ${goal.targetDate}"
                    tvCurrentAmount.text = "R ${String.format("%.2f", current)}"
                    tvTargetAmount.text = "R ${String.format("%.2f", target)}"

                    // Update Progress Bar
                    progressBarGoal.progress = progressPercent
                    progressBarGoal.visibility = View.VISIBLE

                    // Update Pie Chart
                    val pieChartView = findViewById<PieChartView>(R.id.pieChartFeatured)

                    val savedPercentage = if (target > 0) (current / target) * 100 else 0.0
                    val remaining = if (target > current) target - current else 0.0

                    val goalSlices = listOf(
                        PieSlice("Saved", current, savedPercentage, Color.parseColor("#0066cc")),
                        PieSlice(
                            "Remaining",
                            remaining,
                            100.0 - savedPercentage,
                            Color.parseColor("#EAEAEA")
                        )
                    )

                    pieChartView.setData(goalSlices)
                    pieChartView.visibility = View.VISIBLE
                    pieChartView.invalidate()

                    if (!goal.imagePath.isNullOrEmpty()) {
                        val imgFile = File(goal.imagePath)
                        if (imgFile.exists()) {
                            ivGoalImage.setImageURI(imgFile.toUri())
                        }
                    }

                } else {
                    val pieChartView = findViewById<PieChartView>(R.id.pieChartFeatured)
                    pieChartView.setData(emptyList())
                    Log.e("GoalsActivity", "No featured goal found")
                }
            }
        }
    }
    private fun loadAllGoals() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            Log.d("GoalsActivity", "Loading all goals for userId: $userId")

            db.goalDao().getActiveGoals(userId).collect { goals ->
                runOnUiThread {
                    goalsAdapter.updateGoals(goals)
                }
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
            val newGoal = Goal(
                userId = userId,
                goalName = name,
                targetAmount = amount,
                targetDate = date,
                imagePath = selectedImageUri?.toString()
            )
            val newId = db.goalDao().insert(newGoal)
            val savedGoal = newGoal.copy(goalId = newId.toInt())

            FirebaseRepository().saveGoal(savedGoal)

            runOnUiThread {
                Toast.makeText(this@GoalsActivity, "Goal created", Toast.LENGTH_SHORT).show()
                findViewById<TextInputEditText>(R.id.etGoalName).text?.clear()
                findViewById<TextInputEditText>(R.id.etGoalAmount).text?.clear()
                findViewById<TextInputEditText>(R.id.etTargetDate).text?.clear()
                findViewById<ImageView>(R.id.ivNewGoalImage).setImageResource(android.R.drawable.ic_menu_camera)
                findViewById<TextView>(R.id.tvUploadImage).text = "Upload Goal Image"
                selectedImageUri = null
            }
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

                        val updatedGoal = goal.copy(currentAmount = newAmount)
                        FirebaseRepository().saveGoal(updatedGoal)

                        runOnUiThread {
                            Toast.makeText(this@GoalsActivity, "Savings added", Toast.LENGTH_SHORT).show()
                        }
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
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            val goals: List<Goal> = try {
                db.goalDao().getActiveGoals(userId).first()
            } catch (e: Exception) {
                emptyList()
            }

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

                        val updatedGoal = goal.copy(currentAmount = newAmount)
                        FirebaseRepository().saveGoal(updatedGoal)

                        runOnUiThread {
                            Toast.makeText(this@GoalsActivity, "Goal updated", Toast.LENGTH_SHORT).show()
                        }
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
                val spendingGoal = SpendingGoal(
                    userId = userId,
                    minMonthlySpend = min,
                    maxMonthlySpend = max,
                    month = month
                )
                val newId = db.spendingGoalDao().insert(spendingGoal)
                val savedGoal = spendingGoal.copy(id = newId.toInt())

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