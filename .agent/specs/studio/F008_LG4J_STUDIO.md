# F008 fix edge error and update edge label position

## Description

During graph render using React Flow library in  @studio/webui/src/lg4j-graph.js source code i got the following error in browser debug console:
```
[React Flow]: Edge type "conditional" not found. Using fallback type "default"
[React Flow]: Edge type "parallel" not found. Using fallback type "default"
```

## Instructions

I want remove the error by adding the missing edge types to the graph and also update the edge label position to be closer to the target node




