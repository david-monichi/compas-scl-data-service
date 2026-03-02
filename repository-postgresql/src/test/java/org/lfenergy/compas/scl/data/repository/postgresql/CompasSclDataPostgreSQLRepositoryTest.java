// SPDX-FileCopyrightText: 2021 Alliander N.V.
//
// SPDX-License-Identifier: Apache-2.0
package org.lfenergy.compas.scl.data.repository.postgresql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lfenergy.compas.scl.data.model.IArchivedResourcesMetaItem;
import org.lfenergy.compas.scl.data.model.IHistoryMetaItem;
import org.lfenergy.compas.scl.data.model.Version;
import org.lfenergy.compas.scl.data.repository.AbstractCompasSclDataRepositoryTest;
import org.lfenergy.compas.scl.data.repository.CompasSclDataRepository;
import org.lfenergy.compas.scl.extensions.model.SclFileType;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@ExtendWith({MockitoExtension.class, PostgreSQLServerJUnitExtension.class})
class CompasSclDataPostgreSQLRepositoryTest extends AbstractCompasSclDataRepositoryTest {
    private CompasSclDataPostgreSQLRepository repository;

    @Override
    protected CompasSclDataRepository getRepository() {
        return repository;
    }

    @BeforeEach
    void beforeEach() {
        repository = new CompasSclDataPostgreSQLRepository(PostgreSQLServerJUnitExtension.getDataSource());
    }

    @AfterEach
    void afterEach() throws SQLException {
        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.createStatement()) {
            stmt.execute("TRUNCATE TABLE scl_label CASCADE");
            stmt.execute("TRUNCATE TABLE scl_file CASCADE");
            stmt.execute("TRUNCATE TABLE location_resource_tag CASCADE");
            stmt.execute("TRUNCATE TABLE location CASCADE");
            stmt.execute("TRUNCATE TABLE archived_resource CASCADE");
            stmt.execute("TRUNCATE TABLE archived_resource_resource_tag CASCADE");
            stmt.execute("TRUNCATE TABLE resource_tag CASCADE");
        }
    }


    @Test
    public void listHistory_WhenSearchingByAuthorSubstringAndLocation_ShouldReturnMatchingItems() throws SQLException {
        // Given
        UUID locationId1 = createLocation("LOC1", "Test Location 1", "Description");
        UUID locationId2 = createLocation("LOC2", "Test Location 2", "Description");

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        String sclData = """
                    <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                        <Header id="header">
                            <Hitem version="1.0.0" what="Initial version"/>
                        </Header>
                    </SCL>
                """;

        createSclFileWithLocation(id1, "File1", sclData, "John Smith", locationId1);
        createSclFileWithLocation(id2, "File2", sclData, "Jane Smith", locationId2);
        createSclFileWithLocation(id3, "File3", sclData, "Bob Jones", locationId1);

        // When
        List<IHistoryMetaItem> result = repository.listHistory(
                SclFileType.SCD,
                null,
                "Smi",
                locationId1.toString(),
                null,
                null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()),
                () -> assertEquals(id1.toString(), result.get(0).getId()),
                () -> assertEquals(locationId1.toString(), result.get(0).getLocation())
        );
    }

    @Test
    void listHistory_WhenNoMatchingItems_ShouldReturnEmptyList() {
        // When
        List<IHistoryMetaItem> result = repository.listHistory(
                SclFileType.SCD,
                null,
                "NonExistentAuthor",
                UUID.randomUUID().toString(),
                null,
                null
        );

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listHistory_WhenSearchingByAuthorOnly_ShouldReturnAllMatchingItems() throws SQLException {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        String sclData = """
                    <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                        <Header id="header">
                            <Hitem version="1.0.0" what="Initial version"/>
                        </Header>
                    </SCL>
                """;

        createSclFile(id1, "File1", sclData, "John Doe");
        createSclFile(id2, "File2", sclData, "Max Doe");
        createSclFile(id3, "File3", sclData, "Max Smith");

        // When
        List<IHistoryMetaItem> result = repository.listHistory(
                SclFileType.SCD,
                null,
                "Ma",
                null,
                null,
                null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertEquals(id2.toString(), result.get(0).getId()),
                () -> assertEquals(id3.toString(), result.get(1).getId())
        );
    }

    @Test
    void listHistory_WhenSearchingByLocationIdOnly_ShouldReturnAllMatchingItems() throws SQLException {
        // Given
        UUID locationId1 = createLocation("LOC1", "Test Location 1", "Description");
        UUID locationId2 = createLocation("LOC2", "Test Location 2", "Description");

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        String sclData = """
                    <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                        <Header id="header">
                            <Hitem version="1.0.0" what="Initial version"/>
                        </Header>
                    </SCL>
                """;

        createSclFileWithLocation(id1, "File1", sclData, "John Doe", locationId1);
        createSclFileWithLocation(id2, "File2", sclData, "Max Doe", locationId2);
        createSclFileWithLocation(id3, "File3", sclData, "Max Smith", locationId2);

        // When
        List<IHistoryMetaItem> result = repository.listHistory(
                SclFileType.SCD,
                null,
                null,
                locationId2.toString(),
                null,
                null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.stream()
                        .anyMatch(r -> r.getLocation().equals(locationId2.toString())))
        );
    }

    @Test
    void searchArchivedResource_WhenSearchingByAuthorOnly_ShouldReturnMatchingItems() throws SQLException {
        // Given
        UUID locationId = createLocation("LOC1", "Test Location", "Description");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String sclData = """
            <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                <Header id="header">
                    <Hitem version="1.0.0" what="Initial version"/>
                </Header>
            </SCL>
        """;

        createSclFileWithLocation(id1, "File1", sclData, "John Doe", locationId);
        createSclFileWithLocation(id2, "File2", sclData, "Jane Smith", locationId);

        Version version = new Version(1, 0, 0);
        UUID archivedId1 = archiveSclFile(id1, locationId, version, "Approver1", "John Doe");
        UUID archivedId2 = archiveSclFile(id2, locationId, version, "Approver2", "Jane Smith");

        // When
        IArchivedResourcesMetaItem result = repository.searchArchivedResource(
                null, null, "Doe", null, null, null, null, null, null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.getResources().size()),
                () -> assertTrue(result.getResources().stream()
                        .anyMatch(r -> r.getAuthor().equals("John Doe")))
        );
    }

    @Test
    void searchArchivedResource_WhenSearchingByApproverOnly_ShouldReturnMatchingItems() throws SQLException {
        // Given
        UUID locationId = createLocation("LOC1", "Test Location", "Description");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String sclData = """
            <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                <Header id="header">
                    <Hitem version="1.0.0" what="Initial version"/>
                </Header>
            </SCL>
        """;

        createSclFileWithLocation(id1, "File1", sclData, "Author1", locationId);
        createSclFileWithLocation(id2, "File2", sclData, "Author2", locationId);

        Version version = new Version(1, 0, 0);
        archiveSclFile(id1, locationId , version, "Admin User", "Author1");
        archiveSclFile(id2, locationId, version, "Test User", "Author2");

        // When
        IArchivedResourcesMetaItem result = repository.searchArchivedResource(
                null, null, null, "Admin", null, null, null, null, null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.getResources().size()),
                () -> assertTrue(result.getResources().stream()
                        .anyMatch(r -> r.getApprover().equals("Admin User")))
        );
    }

    @Test
    void searchArchivedResource_WhenSearchingByAuthorAndApprover_ShouldReturnMatchingItems() throws SQLException {
        // Given
        UUID locationId = createLocation("LOC1", "Test Location", "Description");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        String sclData = """
            <SCL xmlns="http://www.iec.ch/61850/2003/SCL">
                <Header id="header">
                    <Hitem version="1.0.0" what="Initial version"/>
                </Header>
            </SCL>
        """;

        createSclFileWithLocation(id1, "File1", sclData, "John Smith", locationId);
        createSclFileWithLocation(id2, "File2", sclData, "Jane Doe", locationId);
        createSclFileWithLocation(id3, "File3", sclData, "John Doe", locationId);

        Version version = new Version(1, 0, 0);
        archiveSclFile(id1, locationId, version, "Admin User", "John Smith");
        archiveSclFile(id2, locationId, version, "Test User", "Jane Doe");
        archiveSclFile(id3, locationId, version, "Admin User", "John Doe");

        // When
        IArchivedResourcesMetaItem result = repository.searchArchivedResource(
                null, null, "Joh", "Adm", null, null, null, null, null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.getResources().size()),
                () -> assertTrue(result.getResources().stream()
                    .anyMatch(r -> r.getAuthor().equals("John Smith") || r.getAuthor().equals("John Doe"))),
                () -> assertTrue(result.getResources().stream()
                    .anyMatch(r -> r.getApprover().equals("Admin User")))
        );
    }


    @Test
    void searchArchivedResource_WhenSearchingByNonExistingAuthorAndApprover_ShouldReturnMatchingItems() {
        // When
        IArchivedResourcesMetaItem result = repository.searchArchivedResource(
                null, null, "Joh", "Adm", null, null, null, null, null
        );

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertTrue(result.getResources().isEmpty())
        );
    }

    private UUID createLocation(String key, String name, String description) throws SQLException {
        UUID locationId = UUID.randomUUID();
        String sql = "INSERT INTO location (id, key, name, description) VALUES (?, ?, ?, ?)";

        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, locationId);
            stmt.setString(2, key);
            stmt.setString(3, name);
            stmt.setString(4, description);
            stmt.executeUpdate();
        }
        return locationId;
    }

    private void createSclFileWithLocation(UUID id, String name, String sclData, String author, UUID locationId) throws SQLException {
        String sql = """
                INSERT INTO scl_file(id, major_version, minor_version, patch_version, type, name, created_by, scl_data, location_id, is_deleted)
                VALUES (?, 1, 0, 0, 'SCD', ?, ?, ?, ?, false)
                """;

        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, name);
            stmt.setString(3, author);
            stmt.setString(4, sclData);
            stmt.setObject(5, locationId);
            stmt.executeUpdate();
        }
    }

    private void createSclFile(UUID id, String name, String sclData, String author) throws SQLException {
        String sql = """
                INSERT INTO scl_file(id, major_version, minor_version, patch_version, type, name, created_by, scl_data, is_deleted)
                VALUES (?, 1, 0, 0, 'SCD', ?, ?, ?, false)
        """;

        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, name);
            stmt.setString(3, author);
            stmt.setString(4, sclData);
            stmt.executeUpdate();
        }
    }

    private UUID archiveSclFile(UUID id, UUID locationId ,Version version, String approver, String author) throws SQLException {
        UUID archivedId = UUID.randomUUID();
        UUID referencedResourceId = UUID.randomUUID();

        String insertReferencedResource = """
        INSERT INTO referenced_resource (id, content_type, filename, author, approver, location_id, scl_file_id, scl_file_major_version, scl_file_minor_version, scl_file_patch_version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(insertReferencedResource)) {
            stmt.setObject(1, referencedResourceId);
            stmt.setString(2, "application/xml");
            stmt.setString(3, "archived_file.xml");
            stmt.setString(4, author);
            stmt.setString(5, approver);
            stmt.setObject(6, locationId);
            stmt.setObject(7, id);
            stmt.setInt(8, version.getMajorVersion());
            stmt.setInt(9, version.getMinorVersion());
            stmt.setInt(10, version.getPatchVersion());
            stmt.executeUpdate();
        }

        String insertArchivedResource = """
        INSERT INTO archived_resource (id, archived_at, referenced_resource_id, referenced_resource_major_version, referenced_resource_minor_version, referenced_resource_patch_version)
        VALUES (?, NOW(), ?, ?, ?, ?)
        """;

        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(insertArchivedResource)) {
            stmt.setObject(1, archivedId);
            stmt.setObject(2, referencedResourceId);
            stmt.setInt(3, version.getMajorVersion());
            stmt.setInt(4, version.getMinorVersion());
            stmt.setInt(5, version.getPatchVersion());
            stmt.executeUpdate();
        }

        UUID tagId = UUID.randomUUID();

        String insertTag = "INSERT INTO resource_tag (id, key, value) VALUES (?, ?, ?)";
        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(insertTag)) {
            stmt.setObject(1, tagId);
            stmt.setString(2, "key1");
            stmt.setString(3, "value1");
            stmt.executeUpdate();
        }

        String linkTag = "INSERT INTO archived_resource_resource_tag (archived_resource_id, resource_tag_id) VALUES (?, ?)";
        try (var connection = PostgreSQLServerJUnitExtension.getDataSource().getConnection();
             var stmt = connection.prepareStatement(linkTag)) {
            stmt.setObject(1, archivedId);
            stmt.setObject(2, tagId);
            stmt.executeUpdate();
        }

        return archivedId;
    }

}