# TBD
### Rosa flag

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**rosa** | This flag enables configuration specific to ROSA environments | boolean | - | yes

### Configmap variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**data.collaborationsEnabled** | Collaboration Context feature is enabled | boolean | true | no
**data.pubsubSearchTopicV2** | PubSub topic name for V2 messages (collaboration context) | string | `records-changed-v2` |
