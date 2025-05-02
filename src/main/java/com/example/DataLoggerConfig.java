package com.datalogger;

import net.runelite.client.config.*;

@ConfigGroup("datalogger")
public interface DataLoggerConfig extends Config
{
    @ConfigItem(
            keyName = "outputDirectory",
            name = "Output Location",
            description = "Path to export data to, .csv file or directory",
            position = 1
    )
    default String outputDirectory() { return ""; }

    @ConfigItem(
            keyName = "enableLogging",
            name = "Enable Logging",
            description = "Enables Real Time Data Logging",
            position = 2
    )
    default boolean enableLogging()
    {
        return false;
    }
    enum timestampFormat
    {
        NONE,
        CALENDAR,
        EPOCH
    }
    @ConfigItem(
            keyName = "timestamps",
            name = "Timestamp Format",
            description = "Enables time stamp being logged with each message",
            position = 3
    )
    default timestampFormat timestamps()
    {
        return timestampFormat.NONE;
    }
    enum itemDropOptions
    {
        NONE,
        NAME,
        VALUE,
        BOTH
    }
    @ConfigItem(
            keyName = "itemDrops",
            name = "Item Drops",
            description = "Enables logging of item drops",
            position = 4
    )
    default itemDropOptions itemDrops()
    {
        return itemDropOptions.NONE;
    }
    @ConfigItem(
            keyName = "itemDropPriceThreshold",
            name = "Value Threshold",
            description = "Minimum value of item drops to log",
            position = 5
    )
    default int itemDropPriceThreshold() { return 0; }
    enum xpFormat
    {
        NONE,
        XP_GAINED,
        CURRENT_SKILL_XP
    }
    @ConfigItem(
            keyName = "xpDrops",
            name = "Xp Gained",
            description = "Enables logging of xp drops",
            position = 6
    )
    default xpFormat xpDrops()
    {
        return xpFormat.NONE;
    }
    enum levelFormat
    {
        NONE,
        CURRENT_LEVEL,
        LEVELS_GAINED
    }
    @ConfigItem(
            keyName = "levelups",
            name = "Level Ups",
            description = "Enables logging of skill level ups",
            position = 7
    )
    default levelFormat levelups()
    {
        return levelFormat.NONE;
    }
    @ConfigItem(
            keyName = "monstersKilled",
            name = "NPCs Killed",
            description = "Enables logging of NPC kills",
            position = 8
    )
    default boolean monstersKilled() { return false; }
    @ConfigItem(
            keyName = "damageDealt",
            name = "Damage Dealt",
            description = "Enables logging of player damage dealt",
            position = 9
    )
    default boolean damageDealt() { return false; }
    @ConfigItem(
            keyName = "damageTaken",
            name = "Damage Taken",
            description = "Enables logging of player damage taken",
            position = 10
    )
    default boolean damageTaken() { return false; }
    @ConfigItem(
            keyName = "resourcesGathered",
            name = "Resources Gathered",
            description = "Enables logging of resources gathered",
            position = 11
    )
    default boolean resourcesGathered() { return false; }
    enum messageSettings
    {
        NONE,
        ALL,
        SELF,
        CLAN_CHAT,
        PRIVATE_CHAT,
        FRIENDS_CHAT
    }
    @ConfigItem(
            keyName = "chatMessages",
            name = "Chat Messages",
            description = "Enables logging of chat messages",
            position = 12
    )
    default messageSettings chatMessages() { return messageSettings.NONE; }

}
