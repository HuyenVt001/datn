package com.example.snapget.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.component.common.CommonTopBar

/**
 * Man van ban phap ly tinh (Terms of Service / Privacy Policy) — mo tu Settings.
 * Noi dung hardcode tieng Anh; docType = "terms" | "privacy".
 */
@Composable
fun LegalDocScreen(
    navController: NavHostController = rememberNavController(),
    docType: String = "terms",
) {
    val isTerms = docType == "terms"
    val title = if (isTerms) "Terms of Service" else "Privacy Policy"
    val sections = if (isTerms) termsSections else privacySections

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = title,
                startIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onStartIconClick = { navController.popBackStack() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Last updated: July 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            sections.forEach { (heading, body) ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Noi dung tinh — app la do an tot nghiep, khong co phap ly that; viet "as is" ro rang
private val termsSections: List<Pair<String, String>> = listOf(
    "1. Acceptance of Terms" to
        "By creating an account or using Snapget, you agree to these Terms of Service. " +
        "If you do not agree, please do not use the app.",
    "2. Eligibility" to
        "You must be at least 13 years old to use Snapget. By using the app you confirm " +
        "that you meet this requirement.",
    "3. Your Account" to
        "Snapget uses Firebase Authentication for sign-in. You are responsible for keeping " +
        "your credentials secure and for all activity that happens under your account.",
    "4. Your Content" to
        "You own the photos and videos you share. By posting a moment, you grant Snapget " +
        "a license to store and display it to the friends you have connected with. " +
        "You can delete your moments at any time.",
    "5. Community Rules" to
        "Snapget is built for small, close friend circles: you can connect with up to 20 friends, " +
        "and short videos are limited to 5 seconds. Do not post abusive, illegal, or harmful " +
        "content, and do not use the app to harass others.",
    "6. Termination" to
        "We may suspend or disable accounts that violate these terms or harm the community.",
    "7. Disclaimer & Limitation of Liability" to
        "Snapget is a student graduation project provided \"as is\", without warranties of any kind. " +
        "To the maximum extent permitted by law, we are not liable for any damages arising " +
        "from your use of the app.",
    "8. Changes to These Terms" to
        "We may update these terms from time to time. Continued use of the app after changes " +
        "means you accept the updated terms.",
    "9. Contact" to
        "Questions about these terms? Reach us at support@snapget.app.",
)

private val privacySections: List<Pair<String, String>> = listOf(
    "1. Information We Collect" to
        "We collect the profile information you provide (name, email, avatar, and birthday if " +
        "you add it), the photos and videos you share, and your device push notification token.",
    "2. How We Use It" to
        "Your data is used to deliver moments to your friends, keep streaks and daily quests " +
        "working, and send you notifications about activity in your circle.",
    "3. Sharing" to
        "Your moments are visible only to the friends you have connected with — Snapget has no " +
        "public feed. We rely on trusted processors to run the service: Firebase (authentication, " +
        "database, notifications) and Cloudinary (media storage).",
    "4. Data Retention & Deletion" to
        "Your content stays on your account until you delete it. Deleting a moment removes it " +
        "for everyone.",
    "5. Security" to
        "All traffic between the app and our server is authenticated with your Firebase identity " +
        "token, and media is served over secure connections.",
    "6. Children's Privacy" to
        "Snapget is not intended for children under 13. We do not knowingly collect data from them.",
    "7. Your Rights" to
        "You can edit your name and birthday in Settings, and delete the content you have shared " +
        "at any time from the app.",
    "8. Changes to This Policy" to
        "We may update this policy as the app evolves. Significant changes will be announced in the app.",
    "9. Contact" to
        "Privacy questions? Reach us at support@snapget.app.",
)
