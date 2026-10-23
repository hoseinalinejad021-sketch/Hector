package com.hector.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandPolicyTest {
    @Test
    fun acceptsOnlyExplicitlySupportedCommands() {
        assertEquals("ls", TerminalCommandPolicy.parse("ls").name)
        assertEquals(listOf("notes.txt"), TerminalCommandPolicy.parse("cat notes.txt").args)
        assertEquals(listOf("hello world"), TerminalCommandPolicy.parse("echo \"hello world\"").args)
        assertTrue(TerminalCommandPolicy.parse("pwd").internal)
    }

    @Test
    fun rejectsShellOperatorsAndCommandChaining() {
        listOf("ls; id", "cat notes.txt | id", "echo hi && id", "cat \\${'$'}HOME/file", "cat notes.txt > copy.txt")
            .forEach { command -> assertThrows(IllegalArgumentException::class.java) { TerminalCommandPolicy.parse(command) } }
    }

    @Test
    fun rejectsUnapprovedExecutablesAndOptions() {
        listOf("sh", "bash", "su", "chmod file", "rm -rf notes.txt", "cat --help")
            .forEach { command -> assertThrows(IllegalArgumentException::class.java) { TerminalCommandPolicy.parse(command) } }
    }

    @Test
    fun enforcesArgumentCountsForCopyAndMove() {
        assertThrows(IllegalArgumentException::class.java) { TerminalCommandPolicy.parse("cp only-one") }
        assertThrows(IllegalArgumentException::class.java) { TerminalCommandPolicy.parse("mv a b c") }
        assertEquals(2, TerminalCommandPolicy.parse("cp a b").args.size)
    }

    @Test
    fun rejectsUnterminatedQuotesAndOversizedCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandPolicy.parse("echo \"unfinished")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandPolicy.parse("x".repeat(2001))
        }
    }

    @Test
    fun canonicalPathCannotEscapeWorkspace() {
        val root = java.nio.file.Files.createTempDirectory("hector-terminal-test").toFile()
        try {
            val workspace = File(root, "workspace").apply { mkdirs() }
            val current = File(workspace, "sub").apply { mkdirs() }
            assertEquals(File(workspace, "notes.txt").canonicalFile,
                TerminalCommandPolicy.resolveWorkspacePath(workspace, current, "../notes.txt"))
            assertThrows(IllegalArgumentException::class.java) {
                TerminalCommandPolicy.resolveWorkspacePath(workspace, current, "../../outside.txt")
            }
            assertThrows(IllegalArgumentException::class.java) {
                TerminalCommandPolicy.resolveWorkspacePath(workspace, current, "/system/etc/passwd")
            }
            val escapeLink = File(workspace, "escape")
            java.nio.file.Files.createSymbolicLink(escapeLink.toPath(), root.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                TerminalCommandPolicy.resolveWorkspacePath(workspace, current, "../escape/outside.txt")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
