package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.expr.Type.Companion.ANY_TYPE
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import de.dominicsteinhoefel.pluggabl.theories.Theory
import soot.CharType
import soot.IntType
import soot.VoidType
import java.util.*
import kotlin.collections.LinkedHashMap

open class Type protected constructor(val type: String, private val superType: Type? = null) {
    override fun hashCode() = Objects.hash(Type::class, type)
    override fun equals(other: Any?) = (other as? Type)?.type == type
    override fun toString() = type

    private val allSuperTypes: Set<Type> by lazy {
        allSuperTypes(this)
    }

    fun extends(other: Type) = other == ANY_TYPE || allSuperTypes.contains(other)

    fun commonSuperType(other: Type): Type =
        if (other.extends(this)) this
        else if (this.extends(other)) other
        else superType!!.commonSuperType(other)

    companion object {
        val ANY_TYPE = Type("any", null)

        fun create(type: String, superType: Type) = Type(type, superType)

        private fun allSuperTypes(type: Type?): Set<Type> =
            if (type == null) emptySet()
            else setOf(setOf(type), allSuperTypes(type.superType)).flatten().toSet()
    }
}

val OBJECT_TYPE = Type.create("java.lang.Object", ANY_TYPE)
val NULL_TYPE = Type.create("null", ANY_TYPE)
val VOID_TYPE = Type.create("void", ANY_TYPE)

open class ReferenceType(type: String, superType: Type) : Type(type, superType)
class ArrayType(val baseType: Type) :
    ReferenceType("[$baseType", if (baseType is ArrayType) baseType else OBJECT_TYPE)

class TypeConverter(private val theories: Set<Theory>) {
    private val typesRegistry = LinkedHashMap<soot.Type, Type>()

    fun convert(type: soot.Type): Type {
        return typesRegistry[type] ?: when (type) {
            is VoidType -> VOID_TYPE
            is IntType, is CharType -> IntTheory.getType()
            is soot.RefType -> ReferenceType(type.className, superType(type))
            is soot.ArrayType -> ArrayType(convert(type.baseType))
            else -> TODO("Conversion of type $type not yet implemented.")
        }.also { typesRegistry[type] = it }
    }

    fun typeByName(name: String): Type? =
        typesRegistry.values.firstOrNull { it.type == name }

    private fun superType(type: soot.RefType) =
        if (type.sootClass.hasSuperclass())
            convert(type.sootClass.superclass.type)
        else OBJECT_TYPE
}