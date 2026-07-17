import re

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_auth_state = '''                        is AuthState.Authenticated -> {
                            if (showAdminDashboard && state.user.role == "admin") {
                                AdminDashboardScreen(
                                    authViewModel = authViewModel,
                                    onBack = { showAdminDashboard = false }
                                )
                            } else {
                                MainContent(
                                    viewModel = viewModel, 
                                    isAdmin = state.user.role == "admin",
                                    onAdminClicked = { showAdminDashboard = true },
                                    onLogoutClicked = {
                                        sharedPrefs.edit().remove("email").apply()
                                        authViewModel.logout()
                                    }
                                )
                            }
                        }'''

new_auth_state = '''                        is AuthState.Authenticated -> {
                            if (authViewModel.isOfflineBlocked) {
                                OfflineBlockingScreen()
                            } else if (showAdminDashboard && state.user.role == "admin") {
                                AdminDashboardScreen(
                                    authViewModel = authViewModel,
                                    onBack = { showAdminDashboard = false }
                                )
                            } else {
                                MainContent(
                                    viewModel = viewModel, 
                                    isAdmin = state.user.role == "admin",
                                    onAdminClicked = { showAdminDashboard = true },
                                    onLogoutClicked = {
                                        sharedPrefs.edit().remove("email").apply()
                                        authViewModel.logout()
                                    }
                                )
                            }
                        }'''

content = content.replace(old_auth_state, new_auth_state)

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
