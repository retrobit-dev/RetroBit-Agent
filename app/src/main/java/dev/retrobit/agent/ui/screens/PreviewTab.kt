package dev.retrobit.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.preview.PreviewDevice
import dev.retrobit.agent.preview.PreviewPane
import dev.retrobit.agent.preview.PreviewToolbar
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette

@Composable
fun PreviewTab(
    vm: WorkspaceViewModel,
    onOpenConsole: () -> Unit,
    onOpenLan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project by vm.project.collectAsStateWithLifecycle()
    val fileVersion by vm.fileVersion.collectAsStateWithLifecycle()
    val reloadTick by vm.reloadTick.collectAsStateWithLifecycle()
    val exposed by vm.exposedSecrets.collectAsStateWithLifecycle()
    val console by vm.console.collectAsStateWithLifecycle()
    val lan by vm.lan.collectAsStateWithLifecycle()
    val autoPreview by vm.autoPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var device by rememberSaveable { mutableStateOf(PreviewDevice.PHONE) }

    val current = project ?: return
    val errors = console.count { it.isError }
    val signal = if (autoPreview) fileVersion * 31 + reloadTick else reloadTick.toLong()

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        PreviewToolbar(
            device = device,
            onDeviceChange = { device = it },
            onReload = { vm.reloadPreview() },
            onOpenExternal = if (lan.running) {
                {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lan.url)))
                    }
                }
            } else null,
            errorCount = errors,
            onOpenConsole = onOpenConsole,
        )
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val frameWidth = when (device) {
                PreviewDevice.PHONE -> minOf(maxWidth, 390.dp)
                PreviewDevice.TABLET -> minOf(maxWidth, 768.dp)
                PreviewDevice.DESKTOP -> maxWidth
            }
            Column(
                Modifier
                    .width(frameWidth)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D0D15))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12121B))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Danger.copy(alpha = 0.7f)))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Warning.copy(alpha = 0.7f)))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Success.copy(alpha = 0.7f)))
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Palette.Success.copy(alpha = 0.8f),
                            modifier = Modifier.size(9.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "retrobit.local/${current.name.lowercase().replace(" ", "-").take(22)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = MonoFont,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (lan.running) Palette.Success.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (lan.running) "LAN" else "local",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = if (lan.running) Palette.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = MonoFont,
                        )
                    }
                }
                PreviewPane(
                    filesRoot = vm.filesRoot,
                    design = current.design,
                    exposedSecrets = exposed,
                    database = vm.database,
                    reloadSignal = signal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onConsole = { vm.addConsole(it) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    "● Langsung",
                    color = Palette.Success,
                    background = Palette.Success.copy(alpha = 0.1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${device.label} · ${if (device.widthDp != null) "${device.widthDp}dp" else "fluid"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (lan.running) "${lan.hits} permintaan · ketuk untuk LAN" else "Preview dari file lokal",
                style = MaterialTheme.typography.labelSmall,
                color = if (lan.running) Palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenLan)
                    .padding(4.dp),
            )
        }
    }
}
