actual class SortedSet<E> : MutableSet<E> {
    actual override fun iterator(): MutableIterator<E> {
        TODO("Not yet implemented")
    }

    actual override fun add(element: E): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun remove(element: E): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun addAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun removeAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun retainAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun clear() {
        TODO("Not yet implemented")
    }

    actual override val size: Int
        get() = TODO("Not yet implemented")

    actual override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun contains(element: E): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun containsAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }
}

actual class SortedMap<K, V> : MutableMap<K, V> {
    actual override val keys: MutableSet<K>
        get() = TODO("Not yet implemented")
    actual override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")
    actual override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TODO("Not yet implemented")

    actual override fun put(key: K, value: V): V? {
        TODO("Not yet implemented")
    }

    actual override fun remove(key: K): V? {
        TODO("Not yet implemented")
    }

    actual override fun putAll(from: Map<out K, V>) {
        TODO("Not yet implemented")
    }

    actual override fun clear() {
        TODO("Not yet implemented")
    }

    actual override val size: Int
        get() = TODO("Not yet implemented")

    actual override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun containsKey(key: K): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun containsValue(value: V): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun get(key: K): V? {
        TODO("Not yet implemented")
    }
}

actual val Char.isPrintable: Boolean
    get() = TODO("Not yet implemented")