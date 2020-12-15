package de.dominicsteinhoefel.pluggabl.expr

import soot.CharType
import soot.IntType
import java.util.*
import kotlin.collections.LinkedHashMap

open class Type(val type: String, private val superType: Type? = null) {
    override fun hashCode() = Objects.hash(Type::class, type)
    override fun equals(other: Any?) = (other as? Type)?.type == type
    override fun toString() = type

    fun extends(other: Type) = other == ANY_TYPE || allSuperTypes(this).contains(other)

    companion object {
        private fun allSuperTypes(type: Type?): Set<Type> =
            if (type == null) emptySet()
            else setOf(setOf(type), allSuperTypes(type.superType)).flatten().toSet()
    }

}

val ANY_TYPE = Type("any")
val INT_TYPE = Type("int")
val CHAR_TYPE = Type("char")
val OBJECT_TYPE = Type("java.lang.Object", null)

open class ReferenceType(type: String, superType: Type? = null) : Type(type, superType)
class ArrayType(val baseType: Type) : ReferenceType("[$baseType") // super type?

object TypeConverter {
    private val typesRegistry = LinkedHashMap<soot.Type, Type>()

    fun convert(type: soot.Type): Type {
        return typesRegistry[type] ?: when (type) {
            is IntType -> INT_TYPE
            is CharType -> CHAR_TYPE
            is soot.RefType -> ReferenceType(type.className, superType(type))
            is soot.ArrayType -> ArrayType(convert(type.baseType))
            else -> TODO("Conversion of type $type not yet implemented.")
        }.also { typesRegistry[type] = it }
    }

    private fun superType(type: soot.RefType) =
        if (type.sootClass.hasSuperclass())
            convert(type.sootClass.superclass.type)
        else null
}