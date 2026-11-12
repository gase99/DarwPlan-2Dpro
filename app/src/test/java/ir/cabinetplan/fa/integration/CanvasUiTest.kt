package ir.cabinetplan.fa.integration

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import ir.cabinetplan.fa.MainActivity
import ir.cabinetplan.fa.domain.Tool
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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[CanvasViewModel::class.java]

    @Test fun `Persian RTL screen can draw undo and redo a line`() {
        assertEquals("fa", compose.activity.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.resources.configuration.layoutDirection)
        compose.onNodeWithText("رسم خط").performClick()
        compose.onNodeWithContentDescription("با کشیدن انگشت، بوم را جابه‌جا کنید. با دو انگشت بزرگ‌نمایی کنید.")
            .performTouchInput { swipe(Offset(width * .25f, height * .4f), Offset(width * .7f, height * .65f)) }
        compose.waitUntil { model.state.value.editor.document.lines.size == 1 }
        compose.onNodeWithText("واگرد").performClick()
        compose.waitUntil { model.state.value.editor.document.lines.isEmpty() }
        compose.onNodeWithText("بازانجام").performClick()
        compose.waitUntil { model.state.value.editor.document.lines.size == 1 }
    }

    @Test fun `library placement and Persian save dialog work end to end`() {
        compose.onNodeWithText("کتابخانه").performClick()
        compose.onNodeWithText("کابینت زمینی").performClick()
        compose.waitUntil { model.state.value.editor.tool == Tool.PLACE }
        compose.onNodeWithContentDescription("با کشیدن انگشت، بوم را جابه‌جا کنید. با دو انگشت بزرگ‌نمایی کنید.")
            .performTouchInput { click(center) }
        compose.waitUntil { model.state.value.editor.document.lines.size == 5 }
        compose.onNodeWithText("ذخیره").performClick()
        compose.onNodeWithText("نام طرح").performTextInput("آشپزخانهٔ آزمایش")
        compose.onAllNodesWithText("ذخیره").onLast().performClick()
        compose.waitUntil(15000) { model.state.value.projectName == "آشپزخانهٔ آزمایش" && !model.state.value.busy }
        assertFalse(model.state.value.dirty)
    }
}
