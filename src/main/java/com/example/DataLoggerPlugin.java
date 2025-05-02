package com.datalogger;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.NpcUtil;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.PluginLootReceived;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@PluginDescriptor(
        name = "Data Logger",
        description = "Logs realtime player data to a csv"
)
public class DataLoggerPlugin extends Plugin
{
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ConfigManager configManager;
    @Inject
    private ItemManager itemManager;
    @Inject
    private EventBus eventBus;
    @Inject
    private NpcUtil npcUtil;
    @Inject
    private DataLoggerConfig config;

    private static final String defaultFileName = "dataLog";
    private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
    private static final Set<Integer> SOUL_WARS_REGIONS = ImmutableSet.of(8493, 8749, 9005);
    private static final String skillingActionValidationString1 = "You manage to ";
    private static final String skillingActionValidationString2 = "You catch ";
    private static final String skillingActionValidationString3 = "You get some ";
    private static final String skillingActionValidationString4 = "You manage to ";
    private static final String skillingActionValidationString5 = "You've caught ";
    private static final String skillingActionValidationString6 = "You pick ";
    private static final String skillingActionValidationString7 = "You gather ";
    private static final String skillingActionValidationString8 = "You harvest ";
    enum configOptions
    {
        ITEM_DROPS,
        ITEM_VALUE_THRESHOLD,
        XP_DROPS,
        LVLS_GAINED,
        MONSTERS_KILLED,
        DAMAGE_DEALT,
        DAMAGE_TAKEN,
        RESOURCES_GATHERED,
        CHAT_MESSAGES
    }
    private class skillTracker
    {
        int xp;
        int level;
        int xpgained;
        int lvlGained;
    }

    private File outputFile;
    private boolean logFileCreated = false;
    private List<configOptions> logNeedsUpdated = new ArrayList<>();
    private List<Skill> xpNeedsUpdated = new ArrayList<>();
    private List<Skill> lvlNeedsUpdated = new ArrayList<>();
    private List<String> dropNeedsUpdated = new ArrayList<>();
    private List<Integer> dropValuesNeedsUpdated = new ArrayList<>();
    private List<String> resourceGatheredNeedsUpdated = new ArrayList<>();
    private List<String> resourcesNeedValidated = new ArrayList<>();
    private List<ItemContainerChanged> itemContainerChanges = new ArrayList<>();
    private List<ChatMessage> chatMessagesResources = new ArrayList<>();
    private List<HitsplatApplied> damageDealt = new ArrayList<>();
    private List<HitsplatApplied> damageTaken = new ArrayList<>();
    private List<ChatMessage> chatMessagesNeedUpdated = new ArrayList<>();
    private List<String> monstersKilled = new ArrayList<>();
    Map<Integer, Integer> previousInventoryState = null;
    private boolean bPreviousInventoryInitialized = false;

    HashMap<Skill, skillTracker> xpMap = new HashMap<>();
    private static final String CONFIG_GROUP = "datalogger";

    @Provides
    DataLoggerConfig provideConfig(ConfigManager configManager )
    {
        return configManager.getConfig( DataLoggerConfig.class );
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if ( !logFileCreated )
        {
            clientThread.invoke(() ->
            {
                if (client.getGameState().getState() == GameState.LOGGED_IN.getState())
                {
                    createLogFile();
                }
            });
        }

        clientThread.invoke(() ->
        {
            if (client.getGameState().getState() == GameState.LOGGING_IN.getState())
            {
                logFileCreated = false;
            }
        });
    }

    @Override
    protected void shutDown() throws Exception
    {
        logFileCreated = false;
        bPreviousInventoryInitialized = false;
        xpMap.clear();
        previousInventoryState.clear();
        logNeedsUpdated.clear();
        xpNeedsUpdated.clear();
        lvlNeedsUpdated.clear();
        dropNeedsUpdated.clear();
        dropValuesNeedsUpdated.clear();
        resourceGatheredNeedsUpdated.clear();
        resourcesNeedValidated.clear();
        itemContainerChanges.clear();
        chatMessagesResources.clear();
        damageDealt.clear();
        damageTaken.clear();
        chatMessagesNeedUpdated.clear();
        monstersKilled.clear();
    }

    @Subscribe
    public void onConfigChanged( ConfigChanged event )
    {
        if ( !event.getGroup().equals( CONFIG_GROUP ) )
        {
            return;
        }

        if (client.getGameState().getState() == GameState.LOGGED_IN.getState())
        {
            if ( event.getKey().equals("outputDirectory") )
            {
                logFileCreated = false;
                createLogFile();
            }
        }
    }
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if ( !config.resourcesGathered() )
        {
            return;
        }

        if (event.getContainerId() != InventoryID.INV)
        {
            return;
        }

        itemContainerChanges.add( event );

    }
    public void processItemContainerChange()
    {
        int sz = itemContainerChanges.size();
        for ( int i = sz - 1; i >= 0; --i )
        {
            ItemContainerChanged event = itemContainerChanges.get(i);
            ItemContainer currentItemContainer = event.getItemContainer();
            Map<Integer, Integer> currentItemMap = new HashMap<>();

            for ( Item item : currentItemContainer.getItems() )
            {
                if ( item.getId() != -1 )
                {
                    currentItemMap.put( item.getId(), currentItemContainer.count(item.getId()) );
                }
            }

            if ( !bPreviousInventoryInitialized ) {
                previousInventoryState = new HashMap<>();
                previousInventoryState.putAll( currentItemMap );
                bPreviousInventoryInitialized = true;
            }

            List<ItemStack> itemsToAdd = new ArrayList<>();
            for ( Integer id : currentItemMap.keySet() )
            {
                int countAdded = 0;
                if ( previousInventoryState.containsKey( id ) )
                {
                    countAdded = currentItemMap.get(id) - previousInventoryState.get(id);
                }
                else
                {
                    countAdded = currentItemMap.get(id);
                }

                if ( countAdded > 0 )
                {
                    itemsToAdd.add( new ItemStack( id, countAdded ) );
                    addResourcesToValidate( itemsToAdd );
                }

                itemsToAdd.clear();
            }

            previousInventoryState.clear();
            previousInventoryState.putAll( currentItemMap );
            itemContainerChanges.remove(i);
        }
    }
    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        final Skill skill = statChanged.getSkill();
        final int currentXp = statChanged.getXp();
        final int currentLevel = statChanged.getLevel();
        int xpGained = 0;
        int lvlGained = 0;
        skillTracker tracker;

        if (xpMap.containsKey(skill))
        {
            tracker = xpMap.get( skill );

            xpGained = currentXp - tracker.xp;
            lvlGained = currentLevel - tracker.level;
        }
        else
        {
            tracker = new skillTracker();
        }

        tracker.xp = currentXp;
        tracker.level = currentLevel;
        tracker.xpgained = xpGained;
        tracker.lvlGained = lvlGained;

        xpMap.put( skill, tracker );

        if ( xpGained > 0 && !config.xpDrops().equals(DataLoggerConfig.xpFormat.NONE) )
        {
            logNeedsUpdated.add(configOptions.XP_DROPS);
            xpNeedsUpdated.add(skill);
        }

        if ( lvlGained > 0 && !config.levelups().equals(DataLoggerConfig.levelFormat.NONE))
        {
            logNeedsUpdated.add(configOptions.LVLS_GAINED);
            lvlNeedsUpdated.add(skill);
        }
    }
    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if ( !config.enableLogging() )
        {
            return;
        }

        if ( !logFileCreated )
        {
            createLogFile();
        }

        if ( chatMessagesResources.size() > 0
        ||   itemContainerChanges.size() > 0 )
        {
            logNeedsUpdated.add(configOptions.RESOURCES_GATHERED);
        }

        if ( !logNeedsUpdated.isEmpty() )
        {
            String logString = "";

            int sz = logNeedsUpdated.size();
            String dataString = "";
            for ( int i = sz - 1; i >= 0; --i )
            {
                dataString = "";
                configOptions logToBeUpdated = logNeedsUpdated.get( i );
                if ( config.timestamps().equals( DataLoggerConfig.timestampFormat.CALENDAR ) )
                {
                    LocalDateTime today = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
                    logString = today.format(formatter);
                    logString += ",";
                }
                else if ( config.timestamps().equals( DataLoggerConfig.timestampFormat.EPOCH ) )
                {
                    long now = Instant.now().toEpochMilli();
                    logString = Long.toString( now );
                    logString += ",";
                }

                switch ( logToBeUpdated )
                {
                    case XP_DROPS:
                        dataString = getUpdatedXp();
                        break;
                    case LVLS_GAINED:
                        dataString = getUpdatedLvls();
                        break;
                    case ITEM_DROPS:
                        dataString = getUpdatedItemDrops();
                        break;
                    case RESOURCES_GATHERED:
                        dataString = getUpdatedResourcesGathered();
                        break;
                    case DAMAGE_DEALT:
                        dataString = getUpdatedDamageDone();
                        break;
                    case DAMAGE_TAKEN:
                        dataString = getUpdatedDamageTaken();
                        break;
                    case CHAT_MESSAGES:
                        dataString = getUpdatedChatMessages();
                        break;
                    case MONSTERS_KILLED:
                        dataString = getUpdatedMonstersKilled();
                        break;
                }

                if ( !dataString.equals("") )
                {
                    updateLogFile( logString + dataString );
                }

                logNeedsUpdated.remove( i );
            }

        }
    }
    @Subscribe
    public void onPluginLootReceived(PluginLootReceived event)
    {
        if ( config.itemDrops().equals( DataLoggerConfig.itemDropOptions.NONE ) )
        {
            return;
        }

        final Collection<ItemStack> items = event.getItems();

        addItemDrop( items );
    }
    @Subscribe
    public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
    {
        if ( config.itemDrops().equals( DataLoggerConfig.itemDropOptions.NONE ) )
        {
            return;
        }

        final NPC npc = npcLootReceived.getNpc();
        final Collection<ItemStack> items = npcLootReceived.getItems();

        addItemDrop( items );
    }
    @Subscribe
    public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived)
    {
        if ( config.itemDrops().equals( DataLoggerConfig.itemDropOptions.NONE ) )
        {
            return;
        }

        // Ignore Last Man Standing and Soul Wars player loots
        if (isPlayerWithinMapRegion(LAST_MAN_STANDING_REGIONS) || isPlayerWithinMapRegion(SOUL_WARS_REGIONS))
        {
            return;
        }

        final Player player = playerLootReceived.getPlayer();
        final Collection<ItemStack> items = playerLootReceived.getItems();

        addItemDrop( items );
    }
    @Subscribe
    public void onItemSpawned(ItemSpawned itemSpawned) {

    }
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if ( !config.damageDealt()
        &&   !config.damageTaken()
        &&   !config.monstersKilled() )
        {
            return;
        }
        boolean bDamageTaken = false;

        if ( event.getActor().getName().equals( client.getLocalPlayer().getName() ) )
        {
            bDamageTaken = true;
        }

        Hitsplat hitsplat = event.getHitsplat();

        if ( bDamageTaken
        &&   config.damageTaken() )
        {
            damageTaken.add( event );
            logNeedsUpdated.add( configOptions.DAMAGE_TAKEN );
        }

        if ( hitsplat.isMine()
        &&   config.damageDealt() )
        {
            damageDealt.add( event );
            logNeedsUpdated.add( configOptions.DAMAGE_DEALT );
        }

        if ( hitsplat.isMine()
        &&   config.monstersKilled() )
        {
            Actor actor = event.getActor();
            if (!(actor instanceof NPC))
            {
                return;
            }
            if ( actor.getHealthRatio() <= 0
            ||   npcUtil.isDying((NPC) actor ) )
            {
                monstersKilled.add( actor.getName() );
                logNeedsUpdated.add( configOptions.MONSTERS_KILLED );
            }

        }
    }
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if ( !config.resourcesGathered()
        &&   !config.chatMessages().equals( DataLoggerConfig.messageSettings.NONE ) )
        {
            return;
        }

        if ( config.resourcesGathered() )
        {
            if (event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)
            {
                if (event.getMessage().contains(skillingActionValidationString1)
                ||  event.getMessage().contains(skillingActionValidationString2)
                ||  event.getMessage().contains(skillingActionValidationString3)
                ||  event.getMessage().contains(skillingActionValidationString4)
                ||  event.getMessage().contains(skillingActionValidationString5)
                ||  event.getMessage().contains(skillingActionValidationString6)
                ||  event.getMessage().contains(skillingActionValidationString7)
                ||  event.getMessage().contains(skillingActionValidationString8))
                {
                    chatMessagesResources.add( event );
                }
            }
        }

        if ( !config.chatMessages().equals( DataLoggerConfig.messageSettings.NONE ) )
        {
            if ( event.getName().equals("") )
            {
                return;
            }

            String playerSender = event.getName();
            if ( playerSender.contains(">") )
            {
                playerSender = playerSender.substring( playerSender.indexOf(">") + 1 );
            }

            if ( config.chatMessages().equals( DataLoggerConfig.messageSettings.SELF )
            &&   ( playerSender.equals( client.getLocalPlayer().getName() )
            ||     event.getType() == ChatMessageType.PRIVATECHATOUT ) )
            {
                chatMessagesNeedUpdated.add( event );
                logNeedsUpdated.add( configOptions.CHAT_MESSAGES );
            }
            else if ( config.chatMessages().equals( DataLoggerConfig.messageSettings.CLAN_CHAT )
            &&       ( event.getType() == ChatMessageType.CLAN_CHAT
                    || event.getType() == ChatMessageType.CLAN_GUEST_CHAT
                    || event.getType() == ChatMessageType.CLAN_GIM_CHAT ) )
            {
                chatMessagesNeedUpdated.add( event );
                logNeedsUpdated.add( configOptions.CHAT_MESSAGES );
            }
            else if ( config.chatMessages().equals( DataLoggerConfig.messageSettings.PRIVATE_CHAT )
            &&      ( event.getType() == ChatMessageType.PRIVATECHAT || event.getType() == ChatMessageType.PRIVATECHATOUT ) )
            {
                chatMessagesNeedUpdated.add( event );
                logNeedsUpdated.add( configOptions.CHAT_MESSAGES );
            }
            else if ( config.chatMessages().equals( DataLoggerConfig.messageSettings.FRIENDS_CHAT )
            &&      event.getType() == ChatMessageType.FRIENDSCHAT )
            {
                chatMessagesNeedUpdated.add( event );
                logNeedsUpdated.add( configOptions.CHAT_MESSAGES );
            }
            else if ( config.chatMessages().equals( DataLoggerConfig.messageSettings.ALL ) )
            {
                chatMessagesNeedUpdated.add( event );
                logNeedsUpdated.add( configOptions.CHAT_MESSAGES );
            }
        }
    }

    private void processChatMessagesResouces()
    {
        if ( chatMessagesResources.size() > 0 )
        {
            addValidatedResources( resourcesNeedValidated );
            chatMessagesResources.clear();
        }
        resourcesNeedValidated.clear();
    }
    private String getUpdatedResourcesGathered()
    {
        processItemContainerChange();
        processChatMessagesResouces();

        String s = "";
        int sz = resourceGatheredNeedsUpdated.size();

        if ( sz > 0 )
        {
            s += "ResourceGathered,";
            s += resourceGatheredNeedsUpdated.get( sz - 1 );
            s += ",\n";
            resourceGatheredNeedsUpdated.remove( sz - 1 );

            if ( ( sz - 1 ) > 0 )
            {
                logNeedsUpdated.add( configOptions.RESOURCES_GATHERED );
            }
        }

        return s;
    }
    private String getUpdatedItemDrops()
    {
        String s = "";
        int sz = Math.max( dropNeedsUpdated.size(), dropValuesNeedsUpdated.size() );

        if ( sz > 0 )
        {
            s += "ItemDrop,";
            if ( dropNeedsUpdated.size() > ( sz - 1 ) )
            {
                s += dropNeedsUpdated.get( ( sz - 1 ) );
                s += ",";
                dropNeedsUpdated.remove( ( sz - 1 ) );
            }

            if ( dropValuesNeedsUpdated.size() > ( sz - 1 ) )
            {
                s += Integer.toString( dropValuesNeedsUpdated.get( ( sz - 1 ) ) );
                s += ",";
                dropValuesNeedsUpdated.remove( ( sz - 1 ) );
            }
            s += "\n";

        }


        return s;
    }
    private int getItemValue(int gePrice, int haPrice)
    {
        return Math.max(gePrice, haPrice);
    }
    private String getUpdatedXp()
    {
        String s = "";
        int sz = xpNeedsUpdated.size();
        if ( sz > 0 )
        {
            Skill skillToUpdate = xpNeedsUpdated.get( sz - 1 );

            s += "XPGain,";
            s += skillToUpdate.getName().toString();
            s += ",";

            if ( config.xpDrops().equals(DataLoggerConfig.xpFormat.XP_GAINED ) )
            {
                s += Integer.toString( xpMap.get(skillToUpdate).xpgained );
            }
            else
            {
                s += Integer.toString(( xpMap.get(skillToUpdate).xp ) );
            }
            s += ",\n";

            xpNeedsUpdated.remove( sz - 1 );
        }

        return s;
    }
    private String getUpdatedLvls()
    {
        String s = "";
        int sz = lvlNeedsUpdated.size();
        for ( int i = sz - 1; i >= 0; --i  )
        {
            Skill skillToUpdate = lvlNeedsUpdated.get( i );
            s += "LevelGain,";
            s += skillToUpdate.getName().toString();
            s += ",";
            if ( config.levelups().equals(DataLoggerConfig.levelFormat.LEVELS_GAINED ) )
            {
                s += Integer.toString( xpMap.get(skillToUpdate).lvlGained );
            }
            else
            {
                s += Integer.toString(( xpMap.get(skillToUpdate).level ) );
            }
            s += ",\n";
            lvlNeedsUpdated.remove( i );
        }

        return s;
    }
    private String getUpdatedDamageDone()
    {
        String s = "";
        int sz = damageDealt.size();

        for ( int i = sz - 1; i >= 0; --i )
        {
            HitsplatApplied event = damageDealt.get( i );
            Hitsplat hitsplat = event.getHitsplat();
            int damage = hitsplat.getAmount();

            s += "DamageDealt,";
            s += event.getActor().getName();
            s += ",";
            s += Integer.toString( damage );
            s += ",\n";
            damageDealt.remove( i );
        }

        return s;
    }
    private String getUpdatedDamageTaken()
    {
        String s = "";
        int sz = damageTaken.size();

        if ( sz > 0 )
        {
            HitsplatApplied event = damageTaken.get( sz - 1 );
            Hitsplat hitsplat = event.getHitsplat();
            int damage = hitsplat.getAmount();

            s += "DamageTaken,";
            s += event.getActor().getName();
            s += ",";
            s += Integer.toString( damage );
            s += ",";
            s += "\n";
            damageTaken.remove( sz - 1 );
        }



        return s;
    }
    private String getUpdatedMonstersKilled()
    {
        String s = "";
        int sz = monstersKilled.size();

        if ( sz > 0 )
        {
            String s1 = monstersKilled.get( sz - 1 );
            s += "MonsterKilled";
            s += ",";
            s += monstersKilled.get( sz - 1 );
            s += ",";
            s += "1";
            s += ",\n";
            monstersKilled.remove( sz - 1 );
        }

        return s;
    }
    private String getUpdatedChatMessages()
    {
        String s = "";
        int sz = chatMessagesNeedUpdated.size();

        if ( sz > 0 )
        {
            ChatMessage event = chatMessagesNeedUpdated.get( sz - 1 );

            switch ( event.getType() )
            {
                case PRIVATECHAT:
                    s += "PrivateChatMessage";
                    break;
                case PRIVATECHATOUT:
                    s += "PrivateChatSentMessage";
                    break;
                case CLAN_CHAT:
                case CLAN_GUEST_CHAT:
                case CLAN_GIM_CHAT:
                    s += "ClanChatMessage";
                    break;
                case PUBLICCHAT:
                    s += "PublicChatMessage";
                    break;
                case FRIENDSCHAT:
                    s += "FriendsChatMessage";
                    break;
                default:
                    s += "ChatMessage";
                    break;
            }

            String playerSender = event.getName();

            if ( playerSender.contains(">") )
            {
                playerSender = playerSender.substring( playerSender.indexOf(">") + 1 );
            }

            String regex = "[^a-zA-Z0-9\\s]";
            playerSender = playerSender.replaceAll( regex, " " );
            s += ",";
            s += playerSender;
            s += ",";
            s += event.getMessage().replaceAll( ",", "." );
            s += ",\n";
            chatMessagesNeedUpdated.remove( sz - 1 );
        }

        return s;
    }
    private void resetOutputDirectory()
    {
        Path defaultPath = Paths.get(RuneLite.RUNELITE_DIR.getAbsolutePath(), "datalogger");
        String message = String.format("Output directory reset to default: %s", defaultPath);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
    private void createLogFile() {
        if ( !config.enableLogging() )
        {
            return;
        }

        if ( logFileCreated )
        {
            String message = "Data Logger Plugin is logging data to " + outputFile.getAbsolutePath();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            return;
        }

        clientThread.invoke(() -> {
            String fileName = config.outputDirectory();
            outputFile = new File( fileName );
            if ( outputFile.exists() && !outputFile.isDirectory() )
            {
                logFileCreated = true;
                String message = "Data Logger Plugin is logging data to " + outputFile.getAbsolutePath();
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
                return;
            }

            if ( client.getLocalPlayer() == null
            ||   client.getLocalPlayer().getName() == null )
            {
                return;
            }
            String playerName = client.getLocalPlayer().getName();
            if( !fileName.endsWith(File.separator)) {
                fileName += File.separator;
            }
            fileName += playerName;

            LocalDateTime today = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            fileName +=  '_' + today.format(formatter);
            fileName += ".csv";


            // Ensure output directory exists. If it does not, the config value for output directory
            // is reset to  the default, RUNELITE_DIR/skill_data_exporter/
            outputFile = new File(fileName);

            try
            {
                outputFile.createNewFile();
                String message = "Data Logger Plugin is logging data to " + fileName ;
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            }
            catch ( IOException e )
            {
                log.debug( "Failed to create file" );
            }

            if (!outputFile.exists()) {
                String message = "Output directory invalid. Data not being logged";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
                logFileCreated = true;
            }
            else
            {
                logFileCreated = true;
            }
        });
    }

    private void updateLogFile( String data ) {
        if ( !logFileCreated )
        {
            return;
        }

        try (FileWriter writer = new FileWriter(outputFile, true))
        {
           writer.write(data);
        }
        catch (IOException e) {
            log.error("Failed to write to file", e);
        }
    }
    private boolean isPlayerWithinMapRegion(Set<Integer> definedMapRegions)
    {
        final int[] mapRegions = client.getMapRegions();

        for (int region : mapRegions)
        {
            if (definedMapRegions.contains(region))
            {
                return true;
            }
        }

        return false;
    }

    private void addItemDrop( Collection<ItemStack> items )
    {
        for ( ItemStack item : items )
        {
            ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
            int realItemId = itemComposition.getNote() != -1 ? itemComposition.getLinkedNoteId() : item.getId();
            final int itemVal = getItemValue(itemComposition.getHaPrice(), itemManager.getItemPrice(realItemId) );

            if ( itemVal > config.itemDropPriceThreshold() ) {
                if (config.itemDrops().equals(DataLoggerConfig.itemDropOptions.BOTH)
                        || config.itemDrops().equals(DataLoggerConfig.itemDropOptions.NAME)) {
                    dropNeedsUpdated.add(itemComposition.getName());
                }

                if (config.itemDrops().equals(DataLoggerConfig.itemDropOptions.VALUE)
                        || config.itemDrops().equals(DataLoggerConfig.itemDropOptions.BOTH)) {
                    dropValuesNeedsUpdated.add(itemVal);
                }

                logNeedsUpdated.add(configOptions.ITEM_DROPS);
            }
        }
    }

    private void addResourcesToValidate( Collection<ItemStack> items )
    {
        for ( ItemStack item : items )
        {
            ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
            resourcesNeedValidated.add(itemComposition.getName() + "," + item.getQuantity() );
        }
    }
    private void addValidatedResources( List<String> items )
    {
        resourceGatheredNeedsUpdated.addAll(items);
        for ( String s : items )
        {
            logNeedsUpdated.add( configOptions.RESOURCES_GATHERED );
        }
    }
}
