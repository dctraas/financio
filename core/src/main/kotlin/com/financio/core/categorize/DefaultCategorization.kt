package com.financio.core.categorize

/** A starter category, seeded once into an empty database by the `:app` module's database seeder. */
data class DefaultCategory(val name: String, val colorHex: String)

/** A starter keyword rule, resolved against [DefaultCategorization.CATEGORIES] by name at seed time. */
data class DefaultKeywordRule(val categoryName: String, val keyword: String)

/**
 * The starter set of categories and keyword rules seeded into a brand-new database, so a first
 * import has something to auto-categorize against instead of showing every single transaction
 * as "te controleren" with an empty category list to choose from.
 *
 * Design principles, following how competing budgeting apps (bunq, YNAB, Buddy, Emma) handle
 * this:
 * - Only well-known, single-category merchants get a default rule. A genuinely ambiguous
 *   merchant (a general marketplace like Bol.com or Amazon, which sells everything from
 *   groceries to electronics) is deliberately left out — guessing wrong there is worse than
 *   asking once. The user's one manual choice for it becomes a [com.financio.core.categorize.LearnedRule]
 *   and every future import of that merchant is then automatic too.
 * - Every keyword appears in exactly one category's list, so match order can't silently decide
 *   between two equally-plausible categories for the same brand.
 * - [PRIORITY] sits between the curated example priorities from the architecture doc (1-3) and
 *   [LearnedRule.PRIORITY] (50): a hand-authored exact rule can still override a default, and a
 *   default rule still wins over guessing, but nothing here is meant to be the final word.
 */
object DefaultCategorization {
    const val PRIORITY = 20

    val CATEGORIES = listOf(
        DefaultCategory("Boodschappen", "#5B7A52"),
        DefaultCategory("Vervoer", "#4C6E77"),
        DefaultCategory("Uit eten", "#8A4A3D"),
        DefaultCategory("Abonnementen", "#7A6A45"),
        DefaultCategory("Kleding & verzorging", "#6B6485"),
        DefaultCategory("Wonen & vaste lasten", "#4A5A8A"),
        DefaultCategory("Gezondheid & verzekering", "#3D8A6E"),
        DefaultCategory("Vrije tijd & hobby's", "#9C7A3D"),
        DefaultCategory("Vakantie & reizen", "#3D8FA3"),
        DefaultCategory("Cadeaus & giften", "#A35D82"),
        DefaultCategory("Sparen & beleggen", "#4A8A5D"),
        DefaultCategory("Inkomsten", "#2E7D6B"),
        // Deliberately no rules point here: "Overig" is the manual catch-all, not a match target.
        DefaultCategory("Overig", "#8B9992"),
    )

    val KEYWORD_RULES = listOf(
        // Boodschappen — Dutch supermarket chains.
        "Albert Heijn", "Jumbo", "Lidl", "Aldi", "Coop", "Dirk", "Nettorama", "Spar",
        "Vomar", "Hoogvliet", "Deen", "Ekoplaza", "Picnic", "Boni",
    ).map { DefaultKeywordRule("Boodschappen", it) } + listOf(
        // Vervoer — rail, local transit, fuel, parking, ride-hailing.
        "Nederlandse Spoorwegen", "NS-Fiets", "GVB", "RET", "HTM", "Connexxion", "Arriva",
        "Shell", "BP Tankstation", "Esso", "Tango", "TotalEnergies", "Q-Park", "OV-chipkaart",
        "Uber", "Free Now",
    ).map { DefaultKeywordRule("Vervoer", it) } + listOf(
        // Uit eten — fast food, delivery, coffee chains.
        "McDonald's", "Burger King", "KFC", "Domino's", "New York Pizza", "Thuisbezorgd",
        "Uber Eats", "Starbucks", "Subway", "La Place", "Febo",
    ).map { DefaultKeywordRule("Uit eten", it) } + listOf(
        // Abonnementen — streaming, telecom, recurring digital subscriptions.
        "Netflix", "Spotify", "Videoland", "Disney Plus", "Amazon Prime", "Ziggo", "KPN",
        "T-Mobile", "Vodafone", "Odido", "HBO Max", "YouTube Premium", "Apple.com/bill", "Google Play",
    ).map { DefaultKeywordRule("Abonnementen", it) } + listOf(
        // Kleding & verzorging — clothing retailers and drugstores.
        "H&M", "Zara", "Primark", "C&A", "Bijenkorf", "Zalando", "Wehkamp", "Kruidvat",
        "Etos", "ICI Paris", "Douglas", "Rituals",
    ).map { DefaultKeywordRule("Kleding & verzorging", it) } + listOf(
        // Wonen & vaste lasten — energy, water, housing.
        "Vattenfall", "Eneco", "Essent", "Waternet", "Vitens", "Woningstichting", "Hypotheek", "VvE",
    ).map { DefaultKeywordRule("Wonen & vaste lasten", it) } + listOf(
        // Gezondheid & verzekering — pharmacy, care providers, health insurers.
        "Apotheek", "Huisarts", "CZ Zorgverzekeraar", "VGZ", "Menzis", "Zilveren Kruis",
        "Achmea Zorg", "DSW", "ONVZ", "Fysiotherapie", "Tandarts",
    ).map { DefaultKeywordRule("Gezondheid & verzekering", it) } + listOf(
        // Vrije tijd & hobby's — cinema, attractions, gyms.
        "Pathé", "Kinepolis", "Bioscoop", "Efteling", "Bibliotheek", "Basic-Fit", "ClubSportive",
    ).map { DefaultKeywordRule("Vrije tijd & hobby's", it) } + listOf(
        // Vakantie & reizen — travel booking and airlines.
        "Booking.com", "Airbnb", "Transavia", "KLM", "easyJet", "Ryanair", "TUI",
    ).map { DefaultKeywordRule("Vakantie & reizen", it) } + listOf(
        // Sparen & beleggen — Dutch brokers and savings platforms.
        "DEGIRO", "Brand New Day", "Meesman", "BinckBank", "flatex",
    ).map { DefaultKeywordRule("Sparen & beleggen", it) } + listOf(
        // Inkomsten — generic Dutch salary-payment wording, not tied to one employer.
        "Salaris", "Loonstrook",
    ).map { DefaultKeywordRule("Inkomsten", it) }
}
