package com.eyeguard.app.services

import android.app.admin.DeviceAdminReceiver

/**
 * Device Administration receiver.
 *
 * Being registered as a Device Admin achieves two things on MIUI / Android 13+:
 *   1. The OS marks the app as "protected" — it cannot be force-stopped via
 *      Settings → Apps, and MIUI will not clear it from recent apps.
 *   2. No extra policies (force-lock, etc.) are declared, so the admin role
 *      is the minimum required without side-effects.
 *
 * Activation: user taps "Grant" in PermissionsActivity → system shows the
 * standard Device Admin consent dialog → user confirms.
 * Parents grant admin once; it stays until manually removed in device Settings.
 */
class EyeGuardAdminReceiver : DeviceAdminReceiver()
