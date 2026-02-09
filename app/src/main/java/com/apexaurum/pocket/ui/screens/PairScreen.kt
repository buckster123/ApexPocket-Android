package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.ui.theme.*

/**
 * Pairing screen — enter device token to connect to ApexAurum Cloud.
 * Shown when no token is stored.
 */
@Composable
fun PairScreen(
    onPair: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tokenInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo / title
        Text(
            text = "Au",
            color = Gold,
            fontSize = 48.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "ApexPocket",
            color = TextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "pocket companion",
            color = TextMuted,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(48.dp))

        // Instructions
        Text(
            text = "Pair with your ApexAurum account.\nGet your device token from Settings → Devices.",
            color = TextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Token input
        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Device Token", fontFamily = FontFamily.Monospace) },
            placeholder = { Text("apex_dev_...", color = TextMuted, fontFamily = FontFamily.Monospace) },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = ApexBorder,
                cursorColor = Gold,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = Gold,
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val token = tokenInput.trim()
                if (token.startsWith("apex_dev_") && token.length > 20) {
                    onPair(token)
                } else {
                    error = "Token must start with 'apex_dev_'"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = ApexBlack,
            ),
            enabled = tokenInput.isNotBlank(),
        ) {
            Text(
                "Pair Device",
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
