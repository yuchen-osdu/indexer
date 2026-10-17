# Elasticsearch Alias Support in Indexer Service

## Overview

The OSDU Indexer Service supports Elasticsearch alias-based architecture to enable advanced index lifecycle management. This implementation provides backward compatibility with existing indexes while introducing a versioned physical index pattern for new indexes.

## Architecture

### Conceptual Model

The alias architecture separates **logical index names** from **physical index names**:

- **Logical Index Name**: The name used by applications and APIs (e.g., `tenant-welldb-wellbore-1.0.0`)
- **Physical Index Name**: The actual index in Elasticsearch with revision suffix (e.g., `tenant-welldb-wellbore-1.0.0-r1`)
- **Alias**: An Elasticsearch alias that points the logical name to the physical index

### Benefits

1. **Zero-downtime reindexing**: Point alias to new physical index version
2. **Simplified rollbacks**: Switch alias back to previous physical index
3. **Blue-green deployments**: Test new mappings/settings on new physical indexes

## Implementation Components

### ElasticAliasUtil

Core utility class at `indexer-core/src/main/java/.../util/ElasticAliasUtil.java`:

| Method | Purpose |
|--------|---------|
| `isAlias(client, name)` | Check if a name is an alias |
| `resolveAliasToPhysicalIndexes(client, alias)` | Get physical indexes for an alias |
| `createAlias(client, aliasName, physicalIndex)` | Create alias pointing to physical index |
| `getPhysicalIndexNameForCreation(indexName)` | Generate versioned physical index name (`-r1`) |

### IndicesServiceImpl Changes

#### Index Creation Flow

1. Check if logical name already exists (backward compatibility)
2. Generate physical index name with `-r1` suffix
3. Create the physical index
4. Handle race conditions (concurrent pods may create the same index)
5. Create alias from logical name to physical index
6. Cache and return

#### Index Deletion Flow

1. Resolve the requested name (alias, physical index, or pattern) to physical indexes and their aliases
2. Delete each physical index
3. After each successful deletion, invalidate existence and mapping-sync caches for the physical index, its aliases, and the requested name

Invalidating both physical and logical names allows recovered `-r1` indexes to be
recreated after deletion or force-clean reindex. It also prevents stale logical
cache entries when cleanup deletes a physical index directly.

## Backward Compatibility

Existing indexes created before alias support work unchanged:
- Direct physical index names (no `-r1` suffix)
- No aliases involved
- Deletion works via legacy path

## Operational Considerations

### Physical Index Growth

The alias architecture itself does not automatically create new physical index revisions — the `-r1` suffix is only applied at initial index creation time. The existing `/reindex?force_clean=true` endpoint continues to work as before, recreating the target physical index in place.

However, future features that build on this foundation (such as dual-write and alias-swap) may enable operators to roll over to new physical index revisions (e.g., `-r2`, `-r3`) instead of recreating in place. If operators use this approach, they should monitor and clean up old physical indexes to avoid excessive shard growth in Elasticsearch. Key things to watch:

- **Shard count**: Each physical index consumes shards. Clusters have configurable shard limits (default 1,000 per node).
- **Stale index cleanup**: After confirming a new revision is healthy, operators should delete the old physical index to reclaim resources.
- **Monitoring**: Track alias-to-index mappings via `GET /_cat/aliases` and total index count via `GET /_cat/indices` to detect orphaned physical indexes.

## Testing

### Unit Tests
- `ElasticAliasUtilTest.java` — alias utility methods
- `IndicesServiceRetryTest.java` — retry/idempotency and race conditions
- `IndicesServiceTest.java` — updated for alias behavior

### Integration Tests
- `indexRecord-alias.feature` — alias creation, usage, deletion, backward compatibility, schema merge
- Tags: `@alias`, `@schema-merge`, `@backward-compatibility`
