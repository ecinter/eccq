package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.kernel.core.H2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class PeerH2 {

    static List<Entry> loadPeers() {
        List<Entry> peers = new ArrayList<>();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM peer");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                peers.add(new Entry(rs.getString("address"), rs.getLong("services"), rs.getInt("last_updated")));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return peers;
    }

    static void deletePeers(Collection<Entry> peers) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM peer WHERE address = ?")) {
            for (Entry peer : peers) {
                pstmt.setString(1, peer.getAddress());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void updatePeers(Collection<Entry> peers) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("MERGE INTO peer "
                     + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
            for (Entry peer : peers) {
                pstmt.setString(1, peer.getAddress());
                pstmt.setLong(2, peer.getServices());
                pstmt.setInt(3, peer.getLastUpdated());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void updatePeer(PeerImpl peer) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("MERGE INTO peer "
                     + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
            pstmt.setString(1, peer.getAnnouncedAddress());
            pstmt.setLong(2, peer.getServices());
            pstmt.setInt(3, peer.getLastUpdated());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static class Entry {
        private final String address;
        private final long services;
        private final int lastUpdated;

        Entry(String address, long services, int lastUpdated) {
            this.address = address;
            this.services = services;
            this.lastUpdated = lastUpdated;
        }

        public String getAddress() {
            return address;
        }

        public long getServices() {
            return services;
        }

        public int getLastUpdated() {
            return lastUpdated;
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof Entry) && address.equals(((Entry) obj).address));
        }
    }
}
