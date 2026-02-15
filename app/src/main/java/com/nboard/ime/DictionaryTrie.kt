package com.nboard.ime

internal class DictionaryTrie {
    private class Node {
        val children = HashMap<Char, Node>()
        var isTerminal = false
        var frequency = 0
    }

    private val root = Node()

    fun insert(word: String, frequency: Int) {
        if (word.isBlank() || frequency <= 0) {
            return
        }
        var node = root
        word.forEach { char ->
            node = node.children.getOrPut(char) { Node() }
        }
        node.isTerminal = true
        if (frequency > node.frequency) {
            node.frequency = frequency
        }
    }

    fun frequency(word: String): Int? {
        if (word.isBlank()) {
            return null
        }
        var node = root
        word.forEach { char ->
            node = node.children[char] ?: return null
        }
        return if (node.isTerminal) node.frequency else null
    }

    fun contains(word: String): Boolean {
        return frequency(word) != null
    }
}
