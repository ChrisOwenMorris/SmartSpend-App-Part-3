package com.smartspend

import com.smartspend.data.entity.Expense
import com.smartspend.data.entity.Goal
import com.smartspend.data.entity.SpendingGoal
import com.smartspend.data.entity.Income
import com.smartspend.data.entity.Category
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for SmartSpend core business logic.
 * These tests run locally without a device or emulator.
 */
class SmartSpendUnitTest {

    // ── Expense Tests ──────────────────────────────

    @Test
    fun expense_amountStoredCorrectly() {
        val expense = Expense(
            userId = "user1",
            amount = 250.0,
            description = "Groceries",
            date = "2026-06-03",
            startTime = "09:00",
            endTime = "10:00",
            categoryId = 1,
            receiptPath = null
        )
        assertEquals(250.0, expense.amount, 0.01)
    }

    @Test
    fun expense_defaultIdIsZero() {
        val expense = Expense(
            userId = "user1",
            amount = 50.0,
            description = "Coffee",
            date = "2026-06-03",
            startTime = "08:00",
            endTime = "08:30",
            categoryId = 1,
            receiptPath = null
        )
        assertEquals(0, expense.expenseId)
    }

    // ── Goal Tests ─────────────────────────────────

    @Test
    fun goal_progressCalculatedCorrectly() {
        val goal = Goal(
            userId = "user1",
            goalName = "Holiday",
            targetAmount = 10000.0,
            currentAmount = 2500.0,
            targetDate = "2026-12-31"
        )
        val progress =
            (goal.currentAmount / goal.targetAmount) * 100
        assertEquals(25.0, progress, 0.01)
    }

    @Test
    fun goal_remainingAmountCorrect() {
        val goal = Goal(
            userId = "user1",
            goalName = "Laptop",
            targetAmount = 15000.0,
            currentAmount = 5000.0,
            targetDate = "2026-09-01"
        )
        val remaining =
            goal.targetAmount - goal.currentAmount
        assertEquals(10000.0, remaining, 0.01)
    }

    @Test
    fun goal_completedWhenCurrentMeetsTarget() {
        val goal = Goal(
            userId = "user1",
            goalName = "Emergency Fund",
            targetAmount = 5000.0,
            currentAmount = 5000.0,
            targetDate = "2026-06-30"
        )
        assertTrue(goal.currentAmount >= goal.targetAmount)
    }

    // ── SpendingGoal Tests ─────────────────────────

    @Test
    fun spendingGoal_withinBudget_detected() {
        val goal = SpendingGoal(
            userId = "user1",
            minMonthlySpend = 1000.0,
            maxMonthlySpend = 5000.0,
            month = "2026-06"
        )
        val spend = 3000.0
        assertTrue(
            spend in goal.minMonthlySpend..goal.maxMonthlySpend
        )
    }

    @Test
    fun spendingGoal_overBudget_detected() {
        val goal = SpendingGoal(
            userId = "user1",
            minMonthlySpend = 1000.0,
            maxMonthlySpend = 5000.0,
            month = "2026-06"
        )
        val spend = 7000.0
        assertTrue(spend > goal.maxMonthlySpend)
    }

    // ── Income Tests ───────────────────────────────

    @Test
    fun income_amountStoredCorrectly() {
        val income = Income(
            userId = "user1",
            source = "Salary",
            amount = 25000.0,
            date = "2026-06-01"
        )
        assertEquals(25000.0, income.amount, 0.01)
    }

    // ── Category Tests ─────────────────────────────

    @Test
    fun category_nameStoredCorrectly() {
        val category = Category(
            userId = "user1",
            categoryName = "Food"
        )
        assertEquals("Food", category.categoryName)
    }
}
