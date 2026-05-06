package com.primetech.poc.identity.shared.couchdb.dto.get_db;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DatabaseInfoResponse(
    @JsonProperty("db_name") String dbName,
    @JsonProperty("doc_count") Integer docCount,
    @JsonProperty("doc_del_count") Integer docDelCount,
    @JsonProperty("disk_format_version") Integer diskFormatVersion,
    @JsonProperty("update_seq") String updateSeq,
    @JsonProperty("purge_seq") String purgeSeq,
    @JsonProperty("compact_running") boolean compactRunning,
    DatabaseSizes sizes,
    DatabaseCluster cluster,
    DatabaseProps props
) {
}
