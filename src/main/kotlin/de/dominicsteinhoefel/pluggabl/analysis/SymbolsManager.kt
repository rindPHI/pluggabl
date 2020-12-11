package de.dominicsteinhoefel.pluggabl.analysis

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.FIELD_TYPE
import de.dominicsteinhoefel.pluggabl.util.NewNamesCreator
import soot.SootMethodRef
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JimpleLocal

class SymbolsManager {
    private val localVariables = LinkedHashSet<LocalVariable>()
    private val locVarNamesCreator = NewNamesCreator()
    private val jimpleLocalToLocalVariableMap = LinkedHashMap<JimpleLocal, LocalVariable>()

    private lateinit var heapVariable: LocalVariable
    private lateinit var resultVariable: LocalVariable

    private val functionSymbols = LinkedHashSet<FunctionSymbol>()
    private val funcSymbNamesCreator = NewNamesCreator()
    private val classNameFieldNameToFieldSymbolsMap = LinkedHashMap<Pair<String, String>, FunctionApplication>()
    private val classMethodSigToMethodResultSymbolsMap = LinkedHashMap<Pair<String, String>, FunctionSymbol>()

    fun getLocalVariables() = localVariables.toSet()

    fun newLocalVariable(name: String, type: Type) =
        (LocalVariable(locVarNamesCreator.newName(name), type))
            .also { localVariables.add(it) }

    fun localVariableFor(jLocal: JimpleLocal) =
        jimpleLocalToLocalVariableMap[jLocal]
            ?: (newLocalVariable(jLocal.name, TypeConverter.convert(jLocal.type)))
                .also { jimpleLocalToLocalVariableMap[jLocal] = it }

    fun registerJimpleLocal(jLocal: JimpleLocal): Unit {
        localVariableFor(jLocal)
    }

    fun registerJimpleLocals(jLocals: Collection<JimpleLocal>) = jLocals.forEach { registerJimpleLocal(it) }

    fun registerHeapVar(heapVariable: LocalVariable) {
        if (!this::heapVariable.isInitialized)
            this.heapVariable = heapVariable
                .also { assert(locVarNamesCreator.newName(it.name) == it.name) }
                .also { localVariables.add(it) }
        else
            throw IllegalStateException("Heap variable is already initialized")
    }

    fun registerResultVar(resultVariable: LocalVariable) {
        if (!this::resultVariable.isInitialized)
            this.resultVariable = resultVariable
                .also { assert(locVarNamesCreator.newName(it.name) == it.name) }
                .also { localVariables.add(it) }
        else
            throw IllegalStateException("Result variable is already initialized")
    }

    fun getResultVariable() = resultVariable

    fun getFunctionSymbols() = functionSymbols.toSet()

    fun newFunctionSymbol(name: String, type: Type, paramTypes: List<Type>) =
        FunctionSymbol(
            funcSymbNamesCreator.newName(name),
            type,
            paramTypes
        ).also { functionSymbols.add(it) }

    /**
     * Registers a function symbol under the assumption that the chosen name is not yet used. Use newFunctionSymbol(...)
     * to obtain a function symbols with a (generated) definitely fresh name.
     */
    fun registerFunctionSymbol(functionSymbol: FunctionSymbol) {
        functionSymbols.add(functionSymbol)
        assert(funcSymbNamesCreator.newName(functionSymbol.name) == functionSymbol.name)
    }

    fun registerFunctionSymbols(functionSymbols: Collection<FunctionSymbol>) =
        functionSymbols.forEach { registerFunctionSymbol(it) }

    fun getFieldSymbol(fieldRef: JInstanceFieldRef): FunctionApplication {
        val className = fieldRef.fieldRef.declaringClass().name
        val fieldName = fieldRef.fieldRef.name()

        return getFieldSymbol(className, fieldName) ?: FunctionApplication(
            FunctionSymbol(
                funcSymbNamesCreator.newName(
                    "<$className: ${fieldRef.type} $fieldName>"
                ),
                FIELD_TYPE
            )
        ).also { classNameFieldNameToFieldSymbolsMap[Pair(className, fieldName)] = it }
            .also { functionSymbols.add(it.f) }
    }

    fun getMethodResultSymbol(methodRef: SootMethodRef): FunctionSymbol {
        val className = methodRef.declaringClass.toString()
        val methodSig = "${methodRef.returnType} ${methodRef.name}(${methodRef.parameterTypes.joinToString(", ")})"

        return getMethodResultSymbol(className, methodSig) ?: FunctionSymbol(
            funcSymbNamesCreator.newName(
                "<$className: $methodSig>"
            ),
            TypeConverter.convert(methodRef.returnType),
            methodRef.parameterTypes.map { TypeConverter.convert(it) }.let {
                if (methodRef.isStatic) it
                else listOf(listOf(TypeConverter.convert(methodRef.declaringClass.type)), it).flatten()
            }
        ).also { classMethodSigToMethodResultSymbolsMap[Pair(className, methodSig)] = it }
            .also { functionSymbols.add(it) }
    }

    fun getFieldSymbol(className: String, fieldName: String) =
        classNameFieldNameToFieldSymbolsMap[Pair(className, fieldName)]

    fun getMethodResultSymbol(className: String, methodSig: String) =
        classMethodSigToMethodResultSymbolsMap[Pair(className, methodSig)]
}