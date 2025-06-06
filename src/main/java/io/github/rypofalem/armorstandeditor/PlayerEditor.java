/*
 * ArmorStandEditor: Bukkit plugin to allow editing armor stand attributes
 * Copyright (C) 2016-2023  RypoFalem
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.github.rypofalem.armorstandeditor;

import io.github.rypofalem.armorstandeditor.api.*;
import io.github.rypofalem.armorstandeditor.menu.*;
import io.github.rypofalem.armorstandeditor.modes.Axis;
import io.github.rypofalem.armorstandeditor.modes.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.UUID;

public class PlayerEditor {
    public ArmorStandEditorPlugin plugin;
    private Debug debug;
    Team team;
    private UUID uuid;
    public UUID armorStandInUseId;
    UUID armorStandID;
    EditMode eMode;
    AdjustmentMode adjMode;
    CopySlots copySlots;
    Axis axis;
    double eulerAngleChange;
    double degreeAngleChange;
    double movChange;
    Menu chestMenu;
    ArmorStand target;
    ArrayList<ArmorStand> targetList = null;

    //NEW: ItemFrame Stuff
    ItemFrame frameTarget;
    ArrayList<ItemFrame> frameTargetList = null;
    int targetIndex = 0;
    int frameTargetIndex = 0;
    EquipmentMenu equipMenu;
    PresetArmorPosesMenu presetPoseMenu;
    SizeMenu sizeModificationMenu;
    long lastCancelled = 0;

    public PlayerEditor(UUID uuid, ArmorStandEditorPlugin plugin) {
        this.uuid = uuid;
        this.plugin = plugin;
        this.debug = new Debug(plugin);
        eMode = EditMode.NONE;
        adjMode = AdjustmentMode.COARSE;
        axis = Axis.X;
        copySlots = new CopySlots();
        eulerAngleChange = getManager().coarseAdj;
        degreeAngleChange = eulerAngleChange / Math.PI * 180;
        movChange = getManager().coarseMov;
        chestMenu = new Menu(this);
    }

    public void setMode(EditMode editMode) {
        this.eMode = editMode;
        debug.log("EditMode is: " + editMode.toString().toLowerCase());
        sendMessage("setmode", editMode.toString().toLowerCase());
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
        debug.log("Axis is: " + axis.toString().toLowerCase());
        sendMessage("setaxis", axis.toString().toLowerCase());
    }

    public void setAdjMode(AdjustmentMode adjMode) {
        this.adjMode = adjMode;
        if (adjMode == AdjustmentMode.COARSE) {
            eulerAngleChange = getManager().coarseAdj;
            movChange = getManager().coarseMov;
        } else {
            eulerAngleChange = getManager().fineAdj;
            movChange = getManager().fineMov;
        }
        degreeAngleChange = eulerAngleChange / Math.PI * 180;
        debug.log("AdjMode is: " +adjMode.toString().toLowerCase());
        sendMessage("setadj", adjMode.toString().toLowerCase());
    }

    public void setCopySlot(byte slot) {
        copySlots.changeSlots(slot);
        debug.log("Copy Slot set to: "+ (slot + 1));
        sendMessage("setslot", String.valueOf((slot + 1)));
    }

    public void editArmorStand(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.basic")) {

            armorStand = attemptTarget(armorStand);
            switch (eMode) {
                case LEFTARM:
                    armorStand.setLeftArmPose(subEulerAngle(armorStand.getLeftArmPose()));
                    break;
                case RIGHTARM:
                    armorStand.setRightArmPose(subEulerAngle(armorStand.getRightArmPose()));
                    break;
                case BODY:
                    armorStand.setBodyPose(subEulerAngle(armorStand.getBodyPose()));
                    break;
                case HEAD:
                    armorStand.setHeadPose(subEulerAngle(armorStand.getHeadPose()));
                    break;
                case LEFTLEG:
                    armorStand.setLeftLegPose(subEulerAngle(armorStand.getLeftLegPose()));
                    break;
                case RIGHTLEG:
                    armorStand.setRightLegPose(subEulerAngle(armorStand.getRightLegPose()));
                    break;
                case SHOWARMS:
                    toggleArms(armorStand);
                    break;
                case SIZE:
                    chooseSize(armorStand);
                    break;
                case INVISIBLE:
                    toggleVisible(armorStand);
                    break;
                case BASEPLATE:
                    togglePlate(armorStand);
                    break;
                case GRAVITY:
                    toggleGravity(armorStand);
                    break;
                case COPY:
                    copy(armorStand);
                    break;
                case PASTE:
                    paste(armorStand);
                    break;
                case PLACEMENT:
                    move(armorStand);
                    break;
                case ROTATE:
                    rotate(armorStand);
                    break;
                case DISABLESLOTS:
                    toggleDisableSlots(armorStand);
                    break;
                case VULNERABILITY:
                    toggleInvulnerability(armorStand);
                    break;
                case EQUIPMENT:
                    openEquipment(armorStand);
                    break;
                case RESET:
                    resetPosition(armorStand);
                    break;
                case GLOWING:
                    toggleGlowing(armorStand);
                    break;
                case PRESET:
                    choosePreset(armorStand);
                    break;
                case NONE:
                default:
                    sendMessage("nomode", null);
                    break;

            }
        } else return;
    }

    public void editItemFrame(ItemFrame itemFrame) {
        if (getPlayer().hasPermission("asedit.toggleitemframevisibility") || plugin.invisibleItemFrames) {

            //Generate a new ArmorStandManipulationEvent and call it out.
            ItemFrameManipulatedEvent event = new ItemFrameManipulatedEvent(itemFrame, getPlayer());
            Bukkit.getPluginManager().callEvent(event); // Bukkit handles the call out
            if (event.isCancelled()) return; //do nothing if cancelled

            switch (eMode) {
                case ITEMFRAME:
                    toggleItemFrameVisible(itemFrame);
                    break;
                case RESET:
                    itemFrame.setVisible(true);
                    break;
                case NONE:
                default:
                    sendMessage("nomodeif", null);
                    break;
            }
        } else return;
    }

    private void openEquipment(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.equipment")) return;

        // Dont allow Editing the ArmorStand if the Stand is on the AS-InUse Team
        // Means No 2 Players can edit the Equipment at the same time
        if(!plugin.hasFolia){
            team = plugin.scoreboard.getTeam(plugin.inUseTeam);
            armorStandInUseId = armorStand.getUniqueId();
    
            debug.log("Is ArmorStand currently in use by another player?: " + team.hasEntry(armorStandInUseId.toString()));
    
            if(team != null && !team.hasEntry(armorStandInUseId.toString())){
                debug.log("ArmorStand Not on a Team and Player '" + getPlayer().getDisplayName() + "' has triggered to Open the Equipment Menu, Adding to In Use Team");
                team.addEntry(armorStandInUseId.toString());
                getPlayer().closeInventory();
                equipMenu = new EquipmentMenu(this, armorStand);
                equipMenu.openMenu();
            } else {
                sendMessage("asinuse", "warn");
            }
        } else {
            boolean inUse = Boolean.TRUE.equals(armorStand.getPersistentDataContainer().get(new NamespacedKey(plugin, "inuse"), PersistentDataType.BOOLEAN));

            debug.log("Is ArmorStand currently in use by another player?: " + inUse);

            if (inUse) {
                sendMessage("asinuse", "warn");
            } else {
                debug.log("ArmorStand Not on a Team and Player '" + getPlayer().getDisplayName() + "' has triggered to Open the Equipment Menu. Folia.");
                getPlayer().closeInventory();
                armorStand.getPersistentDataContainer().set(new NamespacedKey(plugin, "inuse"), PersistentDataType.BOOLEAN, true);
                equipMenu = new EquipmentMenu(this, armorStand);
                equipMenu.openMenu();
            }
        }
    }

    private void choosePreset(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.basic")) return;
        debug.log("Player '" + getPlayer().getDisplayName() + "' has triggered the Preset Poses Menu");
        getPlayer().closeInventory();
        presetPoseMenu = new PresetArmorPosesMenu(this, armorStand);
        presetPoseMenu.openMenu();
    }

    //Size Menu Refactor
    private void chooseSize(ArmorStand armorStand){
        if(!getPlayer().hasPermission("asedit.togglesize")){
            sendMessage("nopermoption", "warn", "size");
            return;
        } else {
            if (plugin.getNmsVersion().compareTo("1.21.4") >= 0 || plugin.getNmsVersion().compareTo("v1_21_R3") >= 0) {
                //NOTE: New Sizing Menu ONLY WORKS IN 1.21.3 and HIGHER
                debug.log("Player '" + getPlayer().getDisplayName() + "' has triggered the AS Attribute Size Menu");
                getPlayer().closeInventory();
                sizeModificationMenu = new SizeMenu(ASEHolder.HolderType.SIZE_MENU, this, armorStand);
                sizeModificationMenu.openMenu();
            } else {
                armorStand.setSmall(!armorStand.isSmall());
            }

        }
    }

    public void reverseEditArmorStand(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.basic")) return;

        //Generate a new ArmorStandManipulationEvent and call it out.
        ArmorStandManipulatedEvent event = new ArmorStandManipulatedEvent(armorStand, getPlayer());
        Bukkit.getPluginManager().callEvent(event); // Bukkit handles the call out //TODO: Folia Refactor
        if (event.isCancelled()) return; //do nothing if cancelled

        armorStand = attemptTarget(armorStand);
        switch (eMode) {
            case LEFTARM:
                armorStand.setLeftArmPose(addEulerAngle(armorStand.getLeftArmPose()));
                break;
            case RIGHTARM:
                armorStand.setRightArmPose(addEulerAngle(armorStand.getRightArmPose()));
                break;
            case BODY:
                armorStand.setBodyPose(addEulerAngle(armorStand.getBodyPose()));
                break;
            case HEAD:
                armorStand.setHeadPose(addEulerAngle(armorStand.getHeadPose()));
                break;
            case LEFTLEG:
                armorStand.setLeftLegPose(addEulerAngle(armorStand.getLeftLegPose()));
                break;
            case RIGHTLEG:
                armorStand.setRightLegPose(addEulerAngle(armorStand.getRightLegPose()));
                break;
            case PLACEMENT:
                reverseMove(armorStand);
                break;
            case ROTATE:
                reverseRotate(armorStand);
                break;
            default:
                editArmorStand(armorStand);
        }
    }

    private void move(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.movement")) return;

        //Generate a new ArmorStandManipulationEvent and call it out.
        ArmorStandManipulatedEvent event = new ArmorStandManipulatedEvent(armorStand, getPlayer());
        Bukkit.getPluginManager().callEvent(event); // Bukkit handles the call out //TODO: Folia Refactor
        if (event.isCancelled()) return; //do nothing if cancelled

        Location loc = armorStand.getLocation();
        switch (axis) {
            case X:
                loc.add(movChange, 0, 0);
                break;
            case Y:
                loc.add(0, movChange, 0);
                break;
            case Z:
                loc.add(0, 0, movChange);
                break;
        }
        debug.log("Armorstand will be teleported to: " + loc.getX() + ", " + loc.getY()+ ", " + loc.getZ() + ", near player " + getPlayer().getDisplayName());
        Scheduler.teleport(armorStand, loc);
    }

    private void reverseMove(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.movement")) return;
        Location loc = armorStand.getLocation();
        switch (axis) {
            case X:
                loc.subtract(movChange, 0, 0);
                break;
            case Y:
                loc.subtract(0, movChange, 0);
                break;
            case Z:
                loc.subtract(0, 0, movChange);
                break;
        }
        debug.log("Armorstand will be teleported to: " + loc.getX() + ", " + loc.getY()+ ", " + loc.getZ() + ", near player " + getPlayer().getDisplayName());
        Scheduler.teleport(armorStand, loc);
    }

    private void rotate(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.rotation")) return;
        Location loc = armorStand.getLocation();
        float yaw = loc.getYaw();
        loc.setYaw((yaw + 180 + (float) degreeAngleChange) % 360 - 180);
        debug.log("Armorstand will be teleported to: " + loc.getX() + ", " + loc.getY()+ ", " + loc.getZ() + ", near player " + getPlayer().getDisplayName());
        Scheduler.teleport(armorStand, loc);
    }

    private void reverseRotate(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.rotation")) return;
        Location loc = armorStand.getLocation();
        float yaw = loc.getYaw();
        loc.setYaw((yaw + 180 - (float) degreeAngleChange) % 360 - 180);
        debug.log("Armorstand will be teleported to: " + loc.getX() + ", " + loc.getY()+ ", " + loc.getZ() + ", near player " + getPlayer().getDisplayName());
        Scheduler.teleport(armorStand, loc);
    }

    private void copy(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.copy")) {
            copySlots.copyDataToSlot(armorStand);
            debug.log("ArmorStand Items, Stats and Attributes has been copied to " + (copySlots.currentSlot + 1) + ", near player " + getPlayer().getDisplayName());
            sendMessage("copied", "" + (copySlots.currentSlot + 1));
            setMode(EditMode.PASTE);
        } else {
            sendMessage("nopermoption", "warn", "copy");
        }

    }

    private void paste(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.paste")) {
            ArmorStandData data = copySlots.getDataToPaste();
            debug.log("Pasting ArmorStand Attributes and Settings from: " + (copySlots.currentSlot + 1) + ", near player " + getPlayer().getDisplayName());
            if (data == null) return;
            armorStand.setHeadPose(data.headPos);
            armorStand.setBodyPose(data.bodyPos);
            armorStand.setLeftArmPose(data.leftArmPos);
            armorStand.setRightArmPose(data.rightArmPos);
            armorStand.setLeftLegPose(data.leftLegPos);
            armorStand.setRightLegPose(data.rightLegPos);

            if (plugin.getNmsVersion().compareTo("1.21.4") >= 0 || plugin.getNmsVersion().compareTo("v1_21_R3") >= 0) {
                armorStand.getAttribute(Attribute.SCALE).setBaseValue(data.attributeScale);
            } else {
                armorStand.setSmall(data.size);
            }

            armorStand.setGravity(data.gravity);
            armorStand.setBasePlate(data.basePlate);
            armorStand.setArms(data.showArms);
            armorStand.setVisible(data.visible);

            //Only Paste the Items on the stand if in Creative Mode
            // - Do not run elsewhere for good fecking reason!
            if (this.getPlayer().getGameMode() == GameMode.CREATIVE) {
                armorStand.getEquipment().setHelmet(data.head);
                armorStand.getEquipment().setChestplate(data.body);
                armorStand.getEquipment().setLeggings(data.legs);
                armorStand.getEquipment().setBoots(data.feetsies);
                armorStand.getEquipment().setItemInMainHand(data.rightHand);
                armorStand.getEquipment().setItemInOffHand(data.leftHand);
            }
            sendMessage("pasted", "" + (copySlots.currentSlot + 1));
        } else {
            sendMessage("nopermoption", "warn", "paste");
        }
    }

    private void resetPosition(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.reset")) {
            debug.log("Resetting ArmorStand near the Player " + getPlayer().getDisplayName());
            armorStand.setHeadPose(new EulerAngle(0, 0, 0));
            armorStand.setBodyPose(new EulerAngle(0, 0, 0));
            armorStand.setLeftArmPose(new EulerAngle(0, 0, 0));
            armorStand.setRightArmPose(new EulerAngle(0, 0, 0));
            armorStand.setLeftLegPose(new EulerAngle(0, 0, 0));
            armorStand.setRightLegPose(new EulerAngle(0, 0, 0));
        } else {
            sendMessage("nopermoption", "warn", "reset");
        }
    }

    private void toggleDisableSlots(ArmorStand armorStand) {
        if (!getPlayer().hasPermission("asedit.disableSlots")) {
            sendMessage("nopermoption", "warn", "disableslots");
        } else {
            debug.log("Adding DisabledSlots on ArmorStand near the Player " + getPlayer().getDisplayName());
            if (armorStand.hasEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.REMOVING_OR_CHANGING)) { //Adds a lock to every slot or removes it
                team = Scheduler.isFolia() ? null : plugin.scoreboard.getTeam(plugin.lockedTeam);
                armorStandID = armorStand.getUniqueId();

                for (final EquipmentSlot slot : EquipmentSlot.values()) { // UNLOCKED
                    armorStand.removeEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    armorStand.removeEquipmentLock(slot, ArmorStand.LockType.ADDING);
                }
                getPlayer().playSound(getPlayer().getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);

                if (team != null) {
                    team.removeEntry(armorStandID.toString());
                    armorStand.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 50, 1, false, false)); //300 Ticks = 15 seconds
                }


            } else {
                debug.log("Removing DisabledSlots on ArmorStand near the Player " + getPlayer().getDisplayName());
                for (final EquipmentSlot slot : EquipmentSlot.values()) { //LOCKED
                    armorStand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    armorStand.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
                }
                getPlayer().playSound(getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0f, 1.0f);
                if (team != null) {
                    team.addEntry(armorStandID.toString());
                    armorStand.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 50, 1, false, false)); //300 Ticks = 15 seconds
                }
            }

            sendMessage("disabledslots", null);
        }

    }

    private void toggleInvulnerability(ArmorStand armorStand) { //See NewFeature-Request #256 for more info
        if (getPlayer().hasPermission("asedit.toggleInvulnerability")) {
            debug.log("Making an ArmorStand vulnerable/invulnerable (set armorStand.isInvulnerable() = '"+ !armorStand.isInvulnerable() +"') near player: " + getPlayer().getDisplayName());
            armorStand.setInvulnerable(!armorStand.isInvulnerable());
            sendMessage("toggleinvulnerability", String.valueOf(armorStand.isInvulnerable()));
        } else {
            sendMessage("nopermoption", "warn", "vulnerability");
        }
    }


    private void toggleGravity(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.togglegravity")) {
            debug.log("Toggling the Gravity of an ArmorStand near player: " + getPlayer().getDisplayName());
            armorStand.setGravity(!armorStand.hasGravity());
            sendMessage("setgravity", String.valueOf(armorStand.hasGravity()));//Fix for Wolfst0rm/ArmorStandEditor-Issues#6: Translation of On/Off Keys are broken
        } else {
            sendMessage("nopermoption", "warn", "gravity");
        }
    }

    void togglePlate(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.togglebaseplate")) {
            debug.log("Toggling the Baseplate of an ArmorStand near player: " + getPlayer().getDisplayName());
            armorStand.setBasePlate(!armorStand.hasBasePlate());
        } else {
            sendMessage("nopermoption", "warn", "baseplate");
        }

    }

    void toggleGlowing(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.togglearmorstandglow")) {
            debug.log("Toggling the Glowing Ability of an ArmorStand near player: " + getPlayer().getDisplayName());

            //Will only make it glow white - Not something we can do like with Locking. Do not request this!
            //Otherwise, this simple function becomes a mess to maintain. As you would need a Team generated with each
            //Color and I ain't going to impose that on servers.
            armorStand.setGlowing(!armorStand.isGlowing());
        } else {
            sendMessage("nopermoption", "warn", "armorstandglow");
        }
    }

    void toggleArms(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.togglearms")) {
            debug.log("Toggling the Showing of Arms of an ArmorStand near player: " + getPlayer().getDisplayName());
            armorStand.setArms(!armorStand.hasArms());
        } else {
            sendMessage("nopermoption", "warn", "showarms");
        }
    }

    void toggleVisible(ArmorStand armorStand) {
        if (getPlayer().hasPermission("asedit.togglearmorstandvisibility") || plugin.getArmorStandVisibility()) {
            debug.log("Toggling the Visiblity of an ArmorStand near player: " + getPlayer().getDisplayName());
            armorStand.setVisible(!armorStand.isVisible());
        } else { //Throw No Permission Message
            sendMessage("nopermoption", "warn", "armorstandvisibility");
        }
    }

    void toggleItemFrameVisible(ItemFrame itemFrame) {
        if (getPlayer().hasPermission("asedit.toggleitemframevisibility") || plugin.invisibleItemFrames) { //Option to use perms or Config
            debug.log("Toggling the Visibility of an ItemFrame near player: " + getPlayer().getDisplayName());
            itemFrame.setVisible(!itemFrame.isVisible());
        } else {
            sendMessage("nopermoption", "warn", "itemframevisibility");
        }
    }


    void cycleAxis(int i) {
        int index = axis.ordinal();
        index += i;
        index = index % Axis.values().length;
        while (index < 0) {
            index += Axis.values().length;
        }
        setAxis(Axis.values()[index]);
    }

    private EulerAngle addEulerAngle(EulerAngle angle) {
        switch (axis) {
            case X:
                angle = angle.setX(Util.addAngle(angle.getX(), eulerAngleChange));
                break;
            case Y:
                angle = angle.setY(Util.addAngle(angle.getY(), eulerAngleChange));
                break;
            case Z:
                angle = angle.setZ(Util.addAngle(angle.getZ(), eulerAngleChange));
                break;
            default:
                break;
        }
        return angle;
    }

    private EulerAngle subEulerAngle(EulerAngle angle) {
        switch (axis) {
            case X:
                angle = angle.setX(Util.subAngle(angle.getX(), eulerAngleChange));
                break;
            case Y:
                angle = angle.setY(Util.subAngle(angle.getY(), eulerAngleChange));
                break;
            case Z:
                angle = angle.setZ(Util.subAngle(angle.getZ(), eulerAngleChange));
                break;
            default:
                break;
        }
        return angle;
    }


    public void setTarget(ArrayList<ArmorStand> armorStands) {
        if (armorStands == null || armorStands.isEmpty()) {
            target = null;
            targetList = null;
            sendMessage("notarget", "armorstand");
        } else {
            if (targetList == null) {
                targetList = armorStands;
                targetIndex = 0;
                sendMessage("target", null);
            } else {
                boolean same = targetList.size() == armorStands.size();
                if (same) for (ArmorStand as : armorStands) {
                    same = targetList.contains(as);
                    if (!same) break;
                }

                if (same) {
                    targetIndex = ++targetIndex % targetList.size();
                } else {
                    targetList = armorStands;
                    targetIndex = 0;
                    sendMessage("target", null);
                }
            }

            //API: ArmorStandTargetedEvent
            ArmorStandTargetedEvent e = new ArmorStandTargetedEvent(targetList.get(targetIndex), getPlayer());
            Bukkit.getPluginManager().callEvent(e); //TODO: Folia Refactor
            if (e.isCancelled()) return;

            target = targetList.get(targetIndex);
            highlight(target); //NOTE: If Targeted and Locked, it displays the TEAM Color Glow: RED
            //      Otherwise, its unlocked and will display WHITE as its not in a team by default

        }
    }


    public void setFrameTarget(ArrayList<ItemFrame> itemFrames) {
        if (itemFrames == null || itemFrames.isEmpty()) {
            frameTarget = null;
            frameTargetList = null;
            sendMessage("notarget", "itemframe");
        } else {

            if (frameTargetList == null) {
                frameTargetList = itemFrames;
                frameTargetIndex = 0;
                sendMessage("frametarget", null);
            } else {
                boolean same = frameTargetList.size() == itemFrames.size();
                if (same) for (final ItemFrame itemf : itemFrames) {
                    same = frameTargetList.contains(itemf);
                    if (!same) break;
                }

                if (same) {
                    frameTargetIndex = ++frameTargetIndex % frameTargetList.size();
                } else {
                    frameTargetList = itemFrames;
                    frameTargetIndex = 0;
                    sendMessage("frametarget", null);
                }

                //API: ItemFrameTargetedEvent
                ItemFrameTargetedEvent e = new ItemFrameTargetedEvent(frameTargetList.get(frameTargetIndex), getPlayer());
                Bukkit.getPluginManager().callEvent(e); //TODO: Folia Refactor
                if (e.isCancelled()) return;

                frameTarget = frameTargetList.get(frameTargetIndex);
            }
        }
    }


    ArmorStand attemptTarget(ArmorStand armorStand) {
        if (target == null
            || !target.isValid()
            || target.getWorld() != getPlayer().getWorld()
            || target.getLocation().distanceSquared(getPlayer().getLocation()) > 100)
            return armorStand;
        armorStand = target;
        return armorStand;
    }

    void sendMessage(String path, String format, String option) {
        String message = plugin.getLang().getMessage(path, format, option);
        if (plugin.sendToActionBar) {
            if (ArmorStandEditorPlugin.instance().getHasPaper() || ArmorStandEditorPlugin.instance().getHasSpigot()) { //Paper and Spigot having the same Interaction for sendToActionBar
                plugin.getServer().getPlayer(getUUID()).spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            } else {
                String rawText = plugin.getLang().getRawMessage(path, format, option);
                String command = "minecraft:title %s actionbar %s".formatted(plugin.getServer().getPlayer(getUUID()).getName(), rawText);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } else {
            plugin.getServer().getPlayer(getUUID()).sendMessage(message);
        }
    }

    void sendMessage(String path, String option) {
        sendMessage(path, "info", option);
    }

    private void highlight(ArmorStand armorStand) {
        armorStand.removePotionEffect(PotionEffectType.GLOWING);
        armorStand.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 50, 1, false, false)); //300 Ticks = 15 seconds
    }

    public PlayerEditorManager getManager() {
        return plugin.editorManager;
    }

    public Player getPlayer() {
        return plugin.getServer().getPlayer(getUUID());
    }

    public UUID getUUID() {
        return uuid;
    }

    public void openMenu() {
        if (!isMenuCancelled()) {
            Scheduler.runTaskLater(plugin, new OpenMenuTask(), 1);
        }
    }

    public void cancelOpenMenu() {
        lastCancelled = getManager().getTime();
    }

    boolean isMenuCancelled() {
        return getManager().getTime() - lastCancelled < 2;
    }

    private class OpenMenuTask implements Runnable {

        @Override
        public void run() {
            if (isMenuCancelled()) return;

            //API: PlayerOpenMenuEvent
            PlayerOpenMenuEvent event = new PlayerOpenMenuEvent(getPlayer());
            Bukkit.getPluginManager().callEvent(event); //TODO: Folia Refactor
            if (event.isCancelled()) return;

            chestMenu.openMenu();
        }
    }
}
