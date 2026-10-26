package com.example.calcstudiopro.domain

/** The unit used for trigonometric inputs and inverse-trigonometric outputs. */
enum class AngleUnit {
    DEGREES,
    RADIANS;

    fun toRadians(value: Double): Double = when (this) {
        DEGREES -> Math.toRadians(value)
        RADIANS -> value
    }

    fun fromRadians(value: Double): Double = when (this) {
        DEGREES -> Math.toDegrees(value)
        RADIANS -> value
    }
}