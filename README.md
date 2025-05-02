# Features
- Allows users to log a host of in game data and events to a local .csv file.

# Configuration options
- Enable/Disable logging
- Specify output path: if given a file location it will use the same file and continually append. If given a directory, new log files will be generated in that directory on your first login of a runelite client session.
- Timestamps: Include the timestamp in every entry of your .csv file, with options of no timestamp, calendar format or epoch time format
- Item drops: Log item drops received by the player. Logging options are: none, the name of the item drop, the value of the item drop, or both
- Value Threshold: Sets a minimum item drop value for it to be logged. A value of 0 will log all items. The value determined by High alch price, or ge price, whichever is higher.
- Xp Gained: Log xp drops every time your character receives xp. Logging options are: none, xp gained, or current xp at time of xp drop.
- Level ups: Log level ups every time your character receives a level. Logging options are: None, Level(s) Gained, or Current Level at time of the level up.
- NPCs killed: (Experimental feature) log every NPC that your player deals the finishing blow on.
- Damage dealt: Log every hitsplat your character deals ( not including 0s )
- Damage taken: Log every hitsplat your character receives ( not including 0s ). This does not currently log the source of the damage
- Resources Gathered: Log every gathering skill resources gathered, such as fishing, woodcutting, mining, hunter, and some farming items. The data is not currently logged if there is no corresponding chat message that relates to the resource you're gathering.
- Chat Messages: Log every chat message that your character sends or receives. Logging options are: None, All, Self (only logs messages sent), Clan Chat, Private Chat, Friends Chat