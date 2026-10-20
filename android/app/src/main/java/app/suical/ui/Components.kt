package app.suical.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Nut
import app.suical.domain.Nutrients

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
}

/** Grand titre d'un onglet, avec une ligne de contexte facultative. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Hint(subtitle, Modifier.padding(top = 2.dp))
    }
}

/** Bloc arrondi sur fond de surface : le contenant de base de l'app. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(16.dp)) {
        content()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = modifier,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline)

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(50)) { Text(text, fontWeight = FontWeight.Bold) }
}

/** Action principale mise en avant (jaune) : « Mangé ». */
@Composable
fun AccentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(text) }
}

/** Champ numérique : écrit à chaque frappe valide, et se resynchronise si la valeur change ailleurs. */
@Composable
fun NumField(value: Double, label: String, modifier: Modifier = Modifier, onValue: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(plain(value)) }
    LaunchedEffect(value) { if (parseNum(text) != value) text = plain(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            if (t.all { it.isDigit() || it == '.' || it == ',' }) {
                text = t
                val v = parseNum(t)
                if (v != value) onValue(v)
            }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Champ texte qui n'enregistre qu'une valeur non vide. */
@Composable
fun TextField(value: String, label: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.trim() != value) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; if (it.isNotBlank() && it.trim() != value) onValue(it.trim()) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = modifier,
    )
}

/** Liste déroulante. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, label: String? = null, onSelect: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) Hint(label, Modifier.padding(bottom = 2.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false }) }
            }
        }
    }
}

/** Choix exclusif en pastilles. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, fill: Boolean = false, onSelect: (T) -> Unit) {
    val base = if (fill) Modifier.fillMaxWidth() else Modifier
    Row(base.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).padding(4.dp)) {
        options.forEach { (v, l) ->
            val on = v == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 44.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun Confirm(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

/** kcal / protéines / glucides / lipides. */
@Composable
fun MacroRow(t: Nut) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("kcal" to "kcal", "p" to "prot.", "g" to "gluc.", "l" to "lip.").forEach { (k, lab) ->
            Column(
                Modifier.weight(1f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(nf(t.getValue(k)) + if (k == "kcal") "" else " g", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Hint(lab)
            }
        }
    }
}

/** Détail repliable des micronutriments. */
@Composable
fun NutPanel(t: Nut, label: String, withMacros: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text((if (open) "▾ " else "▸ ") + label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp))
        if (open) {
            if (withMacros) MacroRow(t)
            Nutrients.micros.forEach { n ->
                val v = t.getValue(n.key)
                val pct = (v / n.ref!! * 100).toInt()
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.label, fontSize = 13.sp, modifier = Modifier.weight(1.3f))
                    Text(nf(v, if (v < 10) 1 else 0) + " " + n.unit, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.outline,
                    )
                    Text("$pct %", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.6f).padding(start = 8.dp))
                }
            }
            Hint("Pourcentages des références ANSES pour un homme adulte. Poids crus.", Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
