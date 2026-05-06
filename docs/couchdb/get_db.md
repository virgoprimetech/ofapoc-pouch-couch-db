## Get DB

Retrieves metadata and information about a specific CouchDB database.

**Target Database:** `ofapoc_001$virgodarth$ofa_pms`

This request sends a `GET` request to the CouchDB server at `localhost:5984` and returns detailed information about the named database, including document counts, storage sizes, cluster configuration, and compaction status.

---

### Response Fields

| Field | Description |
| --- | --- |
| `db_name` | The name of the database |
| `doc_count` | Number of active (non-deleted) documents |
| `doc_del_count` | Number of deleted documents |
| `disk_format_version` | The version of the on-disk format used by CouchDB |
| `update_seq` | The current update sequence identifier |
| `purge_seq` | The current purge sequence identifier |
| `compact_running` | Boolean indicating whether compaction is currently running |
| `props` | Additional database properties |
| `sizes.file` | Total size of the database file on disk (in bytes) |
| `sizes.external` | Size of the uncompressed data (in bytes) |
| `sizes.active` | Size of the active data (in bytes) |
| `cluster.q` | Number of shards |
| `cluster.n` | Number of replicas |
| `cluster.w` | Write quorum |
| `cluster.r` | Read quorum |

---

### Example Response

``` json
{
  "db_name": "ofapoc_001$virgodarth$ofa_pms",
  "doc_count": 12,
  "doc_del_count": 0,
  "disk_format_version": 8,
  "compact_running": false,
  "sizes": {
    "file": 315802,
    "external": 5700,
    "active": 16488
  },
  "cluster": { "q": 2, "n": 1, "w": 1, "r": 1 },
  "props": {}
}

 ```