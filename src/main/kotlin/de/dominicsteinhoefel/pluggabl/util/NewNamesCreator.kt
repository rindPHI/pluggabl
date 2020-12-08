package de.dominicsteinhoefel.pluggabl.util

class NewNamesCreator {
    private val names = LinkedHashSet<String>()

    fun newName(proposal: String) =
        if (!names.contains(proposal)) {
            names.add(proposal)
            proposal
        } else {
            var counter = 0
            var currName: String
            do {
                currName = "${proposal}_$counter"
                counter++
            } while (names.contains(currName))
            names.add(currName)
            currName
        }
}