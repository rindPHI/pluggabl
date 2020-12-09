package de.dominicsteinhoefel.pluggabl.expr

import soot.CharType
import soot.IntType
import java.util.*

open class Type(val type: String, private val superTypes: List<Type> = emptyList()) {
    override fun hashCode() = Objects.hash(Type::class, type)
    override fun equals(other: Any?) = (other as? Type)?.type == type
    override fun toString() = type

    fun extends(other: Type) = other == ANY_TYPE || superTypes.contains(other)
}

val ANY_TYPE = Type("any")
val INT_TYPE = Type("int")
val CHAR_TYPE = Type("char")
val OBJECT_TYPE = Type("Object")

open class ReferenceType(type: String) : Type(type)
class ArrayType(val baseType: Type) : ReferenceType("[$baseType")

object TypeConverter {
    fun convert(type: soot.Type): Type {
        return when (type) {
            is IntType -> INT_TYPE
            is CharType -> CHAR_TYPE
            is soot.RefType -> ReferenceType(type.className)
            is soot.ArrayType -> ArrayType(convert(type.baseType))
            else -> TODO("Conversion of type $type not yet implemented.")
        }
    }
}