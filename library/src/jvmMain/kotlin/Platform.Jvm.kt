import java.awt.event.KeyEvent
import java.util.TreeMap
import java.util.TreeSet

actual typealias SortedSet<E> = TreeSet<E>

actual typealias SortedMap<K, V> = TreeMap<K, V>

actual val Char.isPrintable: Boolean
    get() {
        // Copied from https://stackoverflow.com/a/418560/1046374
        return !Character.isISOControl(this) &&
                this != KeyEvent.CHAR_UNDEFINED &&
                Character.UnicodeBlock.of(this).let { it != null && it !== Character.UnicodeBlock.SPECIALS }
    }