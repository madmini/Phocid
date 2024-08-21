package org.sunsetware.phocid.utils

import com.ibm.icu.text.Normalizer2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private val casefolder = Normalizer2.getNFKCCasefoldInstance()

fun Iterable<String>.distinctCaseInsensitive(): List<String> {
    return groupBy { casefolder.normalize(it) }.map { it.value.mode() }
}

@Serializable(with = CaseInsensitiveMapSerializer::class)
class CaseInsensitiveMap<T> private constructor(private val inner: Map<String, T>) :
    Map<String, T> {
    constructor(
        map: Map<String, T>,
        combinator: (List<T>) -> T,
    ) : this(
        map.map { (key, value) -> Pair(casefolder.normalize(key), value) }
            .groupBy({ it.first }, { it.second })
            .map { Pair(it.key, combinator(it.value)) }
            .toMap()
    )

    override val entries
        get() = inner.entries

    override val keys
        get() = inner.keys

    override val size
        get() = inner.size

    override val values
        get() = inner.values

    override fun isEmpty(): Boolean {
        return inner.isEmpty()
    }

    override fun get(key: String): T? {
        return inner[casefolder.normalize(key)]
    }

    override fun containsValue(value: T): Boolean {
        return inner.containsValue(value)
    }

    override fun containsKey(key: String): Boolean {
        return inner.containsKey(casefolder.normalize(key))
    }

    fun <R> map(transform: (Map.Entry<String, T>) -> R): CaseInsensitiveMap<R> {
        return CaseInsensitiveMap(inner.mapValues(transform))
    }

    companion object {
        fun <T> noMerge(map: Map<String, T>): CaseInsensitiveMap<T> {
            return CaseInsensitiveMap(map.mapKeys { casefolder.normalize(it.key) })
        }
    }
}

class CaseInsensitiveMapSerializer<T>(valueSerializer: KSerializer<T>) :
    KSerializer<CaseInsensitiveMap<T>> {
    private val surrogateSerializer = MapSerializer(String.serializer(), valueSerializer)
    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: CaseInsensitiveMap<T>) {
        encoder.encodeSerializableValue(surrogateSerializer, value)
    }

    override fun deserialize(decoder: Decoder): CaseInsensitiveMap<T> {
        return CaseInsensitiveMap(decoder.decodeSerializableValue(surrogateSerializer)) {
            if (it.size != 1) {
                throw IllegalArgumentException(
                    "Deserializing CaseInsensitiveMap with conflicting keys"
                )
            }
            it.first()
        }
    }
}
