package com.opencloud.slee.services.sip.fmfm.jdbc;

import com.opencloud.slee.services.sip.fmfm.FMFMSubscriber;
import com.opencloud.slee.services.sip.fmfm.FMFMSubscriberLookup;

import javax.sql.DataSource;
import javax.slee.Address;
import javax.slee.AddressPlan;
import javax.slee.facilities.Tracer;
import javax.slee.facilities.TraceLevel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Utility class for looking up FMFM subscriber data.
 * <p>
 * DB schema:
 * <pre>
 * create table findme (                   -- for SIP FMFM service
 *  sipaddress varchar(80) not null,       -- subscriber's primary SIP address
 *  backup_sipaddress varchar(80) not null,-- backup SIP address
 *  seq_no integer,                        -- sequence number for ordering bacup addresses
 *  primary key (sipaddress, seq_no)
 * );
 * </pre>
 */
public class JDBCSubscriberLookup implements FMFMSubscriberLookup {
    public JDBCSubscriberLookup(DataSource ds, Tracer tracer) {
        this.ds = ds;
        this.tracer = tracer;
    }

    public FMFMSubscriber lookup(Address address) {
        try {
            return new JDBCFMFMSubscriber(address, ds);
        } catch (Exception e) {
            if (tracer.isTraceable(TraceLevel.FINEST))
                tracer.finest("JDBC query failed", e);
            return null;
        }
    }

    private static class JDBCFMFMSubscriber implements FMFMSubscriber {
        JDBCFMFMSubscriber(Address address, DataSource ds) throws SQLException {
            Connection conn = ds.getConnection();
            PreparedStatement query = conn.prepareStatement(queryGetUser);
            query.setString(1, address.getAddressString());
            ResultSet result = query.executeQuery();
            ArrayList backups = new ArrayList(3); // not expecting more than 3 entries
            while (result.next()) {
                String backupAddress = result.getString("backup_sipaddress");
                if (backupAddress != null) backups.add(new Address(AddressPlan.SIP, backupAddress.trim()));
            }
            result.close();
            query.close();

            this.publicAddress = address;
            this.backupAddresses = (Address[])backups.toArray(new Address[backups.size()]);
        }

        public Address[] getAddresses() {
            // only one public address in DB, but SLEE address profiles may have more
            return new Address[] { publicAddress };
        }

        public Address[] getBackupAddresses() {
            return backupAddresses;
        }

        private final Address publicAddress;
        private final Address[] backupAddresses;
    }

    private final DataSource ds;
    private final Tracer tracer;

    private static final String queryGetUser = "select * from findme where sipaddress = ? order by seq_no";
}
