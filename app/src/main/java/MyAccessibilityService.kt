package com.example.neuronav

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.regex.Pattern

class MyAccessibilityService : AccessibilityService() {

    private var hasClicked = false
    private var hasClickedMeetLink = false
    private var hasJoinedMeet = false
    private var currentPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val newPackageName = event.packageName?.toString()
        if (newPackageName != currentPackage) {
            currentPackage = newPackageName
            hasClicked = false
            hasClickedMeetLink = false
            hasJoinedMeet = false
        }

        val rootNode = rootInActiveWindow ?: return

        when {
            // 💬 WhatsApp automation
            currentPackage.orEmpty().contains("com.whatsapp") -> {
                val prefs = getSharedPreferences("NeuroNavPrefs", MODE_PRIVATE)
                val targetName = prefs.getString("targetContact", null) ?: return

                Handler(Looper.getMainLooper()).postDelayed({
                    if (!hasClicked) {
                        dumpNodeTree(rootNode)
                        val chatRow = findChatRowNode(rootNode, targetName)
                        if (chatRow != null && chatRow.isVisibleToUser) {
                            chatRow.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            hasClicked = true
                            Log.d("NeuroNav", "💬 Opened chat with $targetName")
                            Toast.makeText(this, "Chat opened with $targetName", Toast.LENGTH_SHORT).show()

                            Handler(Looper.getMainLooper()).postDelayed({
                                searchAndClickGMeetLink()
                            }, 2000)
                        } else {
                            Log.d("NeuroNav", "🤷 Contact not found or clickable row missing")
                            Toast.makeText(this, "Couldn’t open chat for $targetName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, 1000)
            }

            // 📲 Google Meet App automation
            currentPackage.orEmpty().contains("com.google.android.apps.meetings") -> {
                if (!hasJoinedMeet) {
                    val micNode = findNodeByTextOrDesc(rootNode, "Turn off microphone")
                        ?: findNodeByTextOrDesc(rootNode, "Microphone")
                    val camNode = findNodeByTextOrDesc(rootNode, "Turn off camera")
                        ?: findNodeByTextOrDesc(rootNode, "Camera")

                    micNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    camNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    val joinNode = findNodeByTextOrDesc(rootNode, "Join")
                        ?: findNodeByTextOrDesc(rootNode, "Ask to join")
                        ?: findNodeByTextOrDesc(rootNode, "Join now")

                    joinNode?.let {
                        it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        hasJoinedMeet = true
                        Toast.makeText(this, "🎥 Auto-joined Meet", Toast.LENGTH_SHORT).show()
                        Log.d("NeuroNav", "✅ Mic off, Cam off, Joined Meet")
                    }
                }
            }
        }
    }

    private fun searchAndClickGMeetLink() {
        val rootNode = rootInActiveWindow ?: return
        if (hasClickedMeetLink) return

        val gmeetClickableNode = findClickableNodeWithLink(rootNode,
            "https?://(www\\.)?meet\\.google\\.com/[a-zA-Z0-9\\-]+|meet\\.google\\.com/[a-zA-Z0-9\\-]+"
        )

        if (gmeetClickableNode != null && gmeetClickableNode.isVisibleToUser) {
            gmeetClickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            hasClickedMeetLink = true
            Log.d("NeuroNav", "📎 Clicked Meet link directly!")
            Toast.makeText(this, "Joining Meet...", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("NeuroNav", "🔍 No clickable GMeet link node found")
        }
    }

    private fun findClickableNodeWithLink(node: AccessibilityNodeInfo?, regex: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val pattern = Pattern.compile(regex)

        val content = node.contentDescription?.toString() ?: node.text?.toString()
        if (pattern.matcher(content ?: "").find() && node.isClickable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findClickableNodeWithLink(child, regex)
            if (result != null) return result
        }

        return null
    }

    private fun findChatRowNode(root: AccessibilityNodeInfo?, contactName: String): AccessibilityNodeInfo? {
        val nodes = root?.findAccessibilityNodeInfosByText(contactName) ?: return null

        for (node in nodes) {
            val clickableAncestor = findAncestor(node, depthLimit = 6) { candidate ->
                candidate.isClickable &&
                        (candidate.className?.contains("RelativeLayout") == true ||
                                candidate.className?.contains("FrameLayout") == true) &&
                        candidate.childCount >= 2 &&
                        candidate.isVisibleToUser
            }

            if (clickableAncestor != null && !isProfilePictureArea(clickableAncestor)) {
                return clickableAncestor
            }
        }

        return null
    }

    private fun findAncestor(
        node: AccessibilityNodeInfo?,
        depthLimit: Int,
        condition: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < depthLimit) {
            if (condition(current)) return current
            current = current.parent
            depth++
        }

        return null
    }

    private fun isProfilePictureArea(node: AccessibilityNodeInfo): Boolean {
        var imageViewCount = 0
        var textViewCount = 0

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            when (child?.className) {
                "android.widget.ImageView" -> imageViewCount++
                "android.widget.TextView" -> textViewCount++
            }
        }

        return imageViewCount >= 1 && textViewCount < 2 && node.childCount <= 3
    }

    private fun findNodeByTextOrDesc(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.text?.toString()?.contains(target, ignoreCase = true) == true) return node
        if (node.contentDescription?.toString()?.contains(target, ignoreCase = true) == true) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByTextOrDesc(child, target)
            if (result != null) return result
        }

        return null
    }

    fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return
        val indent = " ".repeat(depth * 2)
        Log.d(
            "UI-DUMP",
            "$indent Class: ${node.className}, Text: ${node.text}, ContentDesc: ${node.contentDescription}, Clickable: ${node.isClickable}"
        )
        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }
    }

    override fun onInterrupt() {
        Log.d("NeuroNav", "AccessibilityService Interrupted")
    }
}
