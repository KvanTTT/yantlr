package infrastructure

import InfoWithSourceInterval

class InfoWithDescriptor<T : InfoWithSourceInterval>(val info: T, val descriptor: EmbeddedInfoDescriptor<T>) {
    operator fun component1(): T = info

    operator fun component2(): EmbeddedInfoDescriptor<T> = descriptor
}