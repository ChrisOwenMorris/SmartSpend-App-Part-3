package com.smartspend.data

/**
 * Represents a single slice in the Pie Chart.
 */
data class PieSlice(
    val name: String,         // The category name (e.g., "Food")
    val value: Double,        // The raw amount spent
    val percentage: Double,   // Pre-calculated percentage (e.g., 25.5)
    val color: Int            // The color used for this slice
)