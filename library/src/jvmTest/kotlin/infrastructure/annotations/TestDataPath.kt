@file:Suppress("PackageDirectoryMismatch")

package com.intellij.testFramework

/**
 * Specifies the path to testdata for the current test case class.
 * May use the variable $CONTENT_ROOT to specify the module content root or
 * $PROJECT_ROOT to use the project base directory.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class TestDataPath(val value: String)