package com.fertipos.agroshop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.fertipos.agroshop.ui.auth.AuthViewModel
import com.fertipos.agroshop.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegister: () -> Unit
) {
    val vm: AuthViewModel = hiltViewModel()
    val ui = vm.loginState.collectAsState()
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }

    LaunchedEffect(ui.value.success) {
        if (ui.value.success) onLoginSuccess()
    }

    val brandPrimary = Color(0xFF2E7D32)
    val brandSecondary = Color(0xFF66BB6A)
    val brandBackground = Color(0xFFF1F8E9)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to brandBackground,
                    0.6f to Color.White,
                    1f to brandBackground
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_shivam_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .height(120.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Shivam Agro Traders", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = username.value, onValueChange = { username.value = it }, label = { Text("Username") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(8.dp))
            if (ui.value.error != null) {
                Text(text = ui.value.error!!)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.login(username.value, password.value) },
                colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
            ) { Text("Login") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRegister,
                colors = ButtonDefaults.buttonColors(containerColor = brandSecondary)
            ) { Text("Register") }
        }
    }
}
