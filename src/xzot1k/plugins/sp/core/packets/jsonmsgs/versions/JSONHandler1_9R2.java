package xzot1k.plugins.sp.core.packets.jsonmsgs.versions;

import net.minecraft.server.v1_9_R2.IChatBaseComponent;
import net.minecraft.server.v1_9_R2.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import xzot1k.plugins.sp.core.packets.jsonmsgs.JSONHandler;

public class JSONHandler1_9R2 implements JSONHandler
{

    public void sendJSONMessage(Player player, String JSONString)
    {
        IChatBaseComponent comp = IChatBaseComponent.ChatSerializer.a(JSONString);
        PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(comp);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
    }

}
