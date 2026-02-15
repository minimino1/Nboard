package com.nboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTrieTest {
    @Test
    fun insert_keepsHighestFrequencyForSameWord() {
        val trie = DictionaryTrie()

        trie.insert("hello", 5)
        trie.insert("hello", 3)
        trie.insert("hello", 9)

        assertTrue(trie.contains("hello"))
        assertEquals(9, trie.frequency("hello"))
    }

    @Test
    fun insert_ignoresBlankWordsAndNonPositiveFrequencies() {
        val trie = DictionaryTrie()

        trie.insert("", 10)
        trie.insert("ok", 0)
        trie.insert("ok", -2)

        assertFalse(trie.contains(""))
        assertFalse(trie.contains("ok"))
        assertNull(trie.frequency("ok"))
    }

    @Test
    fun contains_checksTerminalNodeOnly() {
        val trie = DictionaryTrie()

        trie.insert("chat", 4)

        assertTrue(trie.contains("chat"))
        assertFalse(trie.contains("cha"))
        assertNull(trie.frequency("cha"))
    }
}
