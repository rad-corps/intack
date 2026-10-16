package app.intack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intack.IntackApp

/**
 * First run (design canvas "Welcome screen"): what Intack is, then start a board for your family or join
 * one you've been invited to. Your name is asked only after choosing. No account, nothing to sign up for.
 */
@Composable
internal fun WelcomeScreen(app: IntackApp, onStart: (groupName: String) -> Unit, onJoin: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(Step.HELLO) }
    var name by rememberSaveable { mutableStateOf(if (app.identity.hasName) app.identity.deviceName else "") }
    var groupName by rememberSaveable { mutableStateOf("Family") }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    if (step != Step.HELLO) BackHandler { step = Step.HELLO }

    fun withName(action: () -> Unit) {
        if (name.isBlank()) {
            nameMissing = true
            return
        }
        app.setName(name)
        action()
    }

    // The story scrolls on small phones; the buttons stay at the bottom.
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            Step.HELLO -> Hello(onStart = { step = Step.START }, onInvite = { step = Step.JOIN })
            Step.START -> Details(
                title = "Start your family board",
                name = name, nameMissing = nameMissing, onName = { name = it; nameMissing = false },
                groupName = groupName, onGroupName = { groupName = it },
                action = "Start the board",
            ) { withName { onStart(groupName) } }
            Step.JOIN -> Details(
                title = "Join with an invite",
                name = name, nameMissing = nameMissing, onName = { name = it; nameMissing = false },
                groupName = null, onGroupName = {},
                action = "Scan or paste the invite",
            ) { withName(onJoin) }
        }
    }
}

private enum class Step { HELLO, START, JOIN }

/** The wordmark and the part above the buttons, scrolling if the phone is short. */
@Composable
private fun ColumnScope.Story(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Wordmark(fontSize = 30.sp)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun ColumnScope.Hello(onStart: () -> Unit, onInvite: () -> Unit) {
    Story {
        PinnedNotes()
        Spacer(Modifier.height(28.dp))
        Text(
            "A noticeboard for your family",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Brand.Serif, fontSize = 32.sp, lineHeight = 37.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Calendar, lists and messages that stay with the people they're for.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(20.dp))
    BrandButton("Start a family board", onClick = onStart)
    Spacer(Modifier.height(10.dp))
    BrandButton("I have an invite", outlined = true, onClick = onInvite)
    Spacer(Modifier.height(16.dp))
    Text("No account or email needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Your name (so the others know who added what), and the board's name when starting one. */
@Composable
private fun ColumnScope.Details(
    title: String,
    name: String,
    nameMissing: Boolean,
    onName: (String) -> Unit,
    groupName: String?,
    onGroupName: (String) -> Unit,
    action: String,
    onDone: () -> Unit,
) {
    Story {
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Brand.Serif), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Your first name") },
            supportingText = { Text(if (nameMissing) "Please enter your name first" else "So the others know who added what") },
            isError = nameMissing,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (groupName != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = groupName,
                onValueChange = onGroupName,
                label = { Text("Name your board") },
                supportingText = { Text("Your household, or anyone you plan things with") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    BrandButton(action, onClick = onDone)
    Spacer(Modifier.height(16.dp))
    Text(
        "Your plans stay on your family's phones. Only the people you invite can see them.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Three notes pinned to the board: a calendar entry, a shopping list and a message. */
@Composable
private fun PinnedNotes() {
    Box(Modifier.fillMaxWidth().height(300.dp).clearAndSetSemantics {}) {
        Note(Modifier.offset(14.dp, 18.dp).width(178.dp), -4f, Brand.Card, Brand.Marigold) {
            NoteLabel("CALENDAR", Brand.Pine)
            Text("Sat · Footy, 9am", fontFamily = Brand.Serif, fontSize = 20.sp, lineHeight = 23.sp, color = Brand.Ink)
            Text("Dad's driving", fontSize = 13.sp, color = Brand.Muted)
        }
        Note(Modifier.align(Alignment.TopEnd).offset((-8).dp, 64.dp).width(168.dp), 3f, Brand.Pine, Brand.Marigold) {
            NoteLabel("SHOPPING", Color(0xFFCFE6DE))
            Text("Milk\nBread\nBirthday candles", fontSize = 15.sp, lineHeight = 22.sp, color = Color.White)
        }
        Note(Modifier.offset(56.dp, 178.dp).width(196.dp), -1.5f, Brand.Marigold, Brand.Pine) {
            NoteLabel("MESSAGES", Brand.Ink)
            Text("“Home by six?”", fontSize = 15.sp, color = Brand.Ink)
        }
    }
}

@Composable
private fun Note(modifier: Modifier, angle: Float, fill: Color, pin: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.rotate(angle)) {
        Column(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(14.dp), clip = false)
                .background(fill, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
        PinDot(pin, 12.dp, Modifier.align(Alignment.TopCenter).offset(y = 8.dp))
    }
}

@Composable
private fun NoteLabel(text: String, color: Color) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = color)
}

/** A round pin head, as seen from the front on the board. */
@Composable
internal fun PinDot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).shadow(2.dp, CircleShape).background(color, CircleShape))
}

/** The brand's full-width button: 56 dp tall, 16 dp corners; [outlined] for the second choice. */
@Composable
internal fun BrandButton(text: String, outlined: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val modifier = Modifier.fillMaxWidth().height(56.dp)
    val label: @Composable () -> Unit = { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    if (outlined) {
        androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier, shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { label() }
    } else {
        androidx.compose.material3.Button(onClick = onClick, modifier = modifier, shape = shape) { label() }
    }
}

/** A quieter text button in the brand's shape, for a third choice. */
@Composable
internal fun BrandTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
