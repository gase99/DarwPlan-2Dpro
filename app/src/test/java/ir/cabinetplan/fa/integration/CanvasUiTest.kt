package ir.cabinetplan.fa.integration

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import ir.cabinetplan.fa.MainActivity
import ir.cabinetplan.fa.domain.ViewMode
import ir.cabinetplan.fa.ui.canvas.CanvasViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val model: CanvasViewModel
        get() = ViewModelProvider(compose.activity)[CanvasViewModel::class.java]

    @Test
    fun `Persian RTL screen draws wall supports undo redo and elevation switch`() {
        assertEquals("fa", compose.activity.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.resources.configuration.layoutDirection)

        val initialWallCount = model.state.value.editor.document.lines.size
        compose.onNodeWithText("رسم دیوار").performScrollTo().performClick()
        compose.onNodeWithContentDescription("با کشیدن انگشت، بوم را جابه‌جا یا ترسیم کنید. با دو انگشت بزرگ‌نمایی کنید.")
            .performTouchInput {
                swipe(Offset(width * 0.2f, height * 0.4f), Offset(width * 0.75f, height * 0.4f))
            }

        compose.waitUntil(5000) { model.state.value.editor.document.lines.size == initialWallCount + 1 }

        compose.onNodeWithText("واگرد").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.editor.document.lines.size == initialWallCount }

        compose.onNodeWithText("بازانجام").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.editor.document.lines.size == initialWallCount + 1 }

        compose.onNodeWithText("نمای روبرو").performClick()
        compose.waitUntil(5000) { model.state.value.editor.viewMode == ViewMode.ELEVATION_FRONT }
    }

    @Test
    fun `catalog addition and local Room save work end to end`() {
        val initialCabCount = model.state.value.editor.document.cabinets.size
        compose.onNodeWithText("کتابخانه").performClick()
        compose.onNodeWithText("کابینت زمینی یک‌درب").performClick()

        compose.waitUntil(5000) { model.state.value.editor.document.cabinets.size == initialCabCount + 1 }
        assertTrue(model.state.value.dirty)

        compose.onAllNodesWithText("ذخیره").onFirst().performClick()
        compose.waitUntil(15000) { !model.state.value.dirty && !model.state.value.busy }
        assertNotNull(model.state.value.projectId)
    }
}
