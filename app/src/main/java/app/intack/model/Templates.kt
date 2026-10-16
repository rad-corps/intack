package app.intack.model

/** A kind of list to start from: its suggested name, and the headings it starts with. */
class ListTemplate(val id: String, val label: String, val name: String, val headings: List<String>)

val BLANK_LIST = ListTemplate("blank", "Blank", "", emptyList())

val GROCERIES = ListTemplate("groceries", "Groceries", "Groceries", listOf("Fresh", "Bakery", "Fridge", "Freezer", "Pantry", "Household"))

val LIST_TEMPLATES = listOf(
    BLANK_LIST,
    GROCERIES,
    ListTemplate("todo", "To do", "To do", listOf("Today", "This week", "Some time")),
    ListTemplate("packing", "Packing", "Packing", listOf("Clothes", "Toiletries", "Documents", "Chargers", "For the kids")),
    ListTemplate("meals", "Meal plan", "Meal plan", listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")),
)
