@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.test

/**
 * Used by DevKit IDE Plugin to find "related testData".
 * Plugin provides various IDE-assistance (e.g. "NavigateToTestData"-actions and gutter icons).
 *
 * The main annotation is @[com.intellij.testFramework.TestDataPath].
 * @[TestDataPath] is usually set on the base class, and @[TestMetadata] - on test methods.
 * Without @[TestMetadata], a path based on test name is computed:
 *
 * <table summary="">
 * <tr> <th>Lookup rule</th>            <th>Test name</th>  <th>Related testData path</th> </tr>
 * <tr> <td>default</td>                <td>testFoo</td>    <td>'{argument-of-@TestDataPath}/foo'</td> </tr>
 * <tr> <td>with @[TestMetadata]</td>   <td>testFoo</td>    <td>'{argument-of-@TestDataPath}/{argument-of-@TestMetadata}'</td> </tr>
</table> *
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
annotation class TestMetadata(val value: String)