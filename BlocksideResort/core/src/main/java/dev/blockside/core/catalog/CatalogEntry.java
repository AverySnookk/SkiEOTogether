package dev.blockside.core.catalog;

/** One purchasable project. Plain fields so it loads straight from catalog.json. */
public final class CatalogEntry {
    public String id;
    public String name;
    public EntryKind kind = EntryKind.BUILDING;
    public String category = "guest_services";
    public long price;
    public long laborCost;
    public int requiredLevel = 1;
    public double requiredReputation = 0;
    /** Template id under blockside/templates, or null if the building is a catalog placeholder for later. */
    public String template;
    /** For lifts: template for the upper station. */
    public String upperTemplate;
    public String service = "";
    /** Guests/hour served (buildings) or riders/hour (lifts). */
    public int capacity;
    public String description = "";
    public double powerPerHour;
    public double waterPerHour;
    public long upkeepPerHour;
    /** Item granted for EQUIPMENT entries (e.g. blockside:snow_gun). */
    public String item;
    /** Lift settings. */
    public double liftSpeed = 2.5;
    public int seatsPerChair = 2;
    public double chairSpacing = 7;
    public int maxLength = 140;

    public boolean available() { return kind == EntryKind.EQUIPMENT ? item != null : template != null; }
}
