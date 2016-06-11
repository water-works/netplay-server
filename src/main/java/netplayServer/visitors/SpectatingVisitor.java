package netplayServer.visitors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import netplayServer.Client;
import netplayServer.Client.ClientStatus;
import netplayprotos.NetplayServiceProto.KeyStatePB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;

public class SpectatingVisitor implements OutgoingEventVisitor {
	
	// Maps client to the console they are spectating.
	static Map<Long, Set<Client>> spectatingMap = Maps.newConcurrentMap();
	
	static boolean addSpectator(Client client, long consoleId) {
		if (spectatingMap.containsKey(consoleId)) {
			if (spectatingMap.get(consoleId).contains(client)) {
				return false;
			}
		} else {
			spectatingMap.put(consoleId, Sets.<Client>newConcurrentHashSet());
		}
		if (client.getStatus() != ClientStatus.CREATED) {
			return false;
		}
		spectatingMap.get(consoleId).add(client);
		return true;
	}
	
	/**
	 * If we see keypresses, start the game and start sending them.
	 */
	@Override
	public void visit(OutgoingEventPB event) {
		if (event.getKeyPressCount() > 0) {
			handleKeypresses(event.getKeyPressList());
		}
	}

	private void handleKeypresses(List<KeyStatePB> keyList) {
		for (KeyStatePB key : keyList) {
			if (!spectatingMap.containsKey(key.getConsoleId())) {
				return;
			}
			List<KeyStatePB> indivdualKeyList = new ArrayList<KeyStatePB>();
			indivdualKeyList.add(key);
			for (Client client : spectatingMap.get(key.getConsoleId())) {
				client.acceptKeyPresses(indivdualKeyList);
			}
		}
	}
}
