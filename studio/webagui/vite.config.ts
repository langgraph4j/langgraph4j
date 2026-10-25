import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  build: {
    rolldownOptions: {
      output: {
        // Keep large third-party libraries in cacheable chunks instead of one oversized application bundle.
        codeSplitting: {
          groups: [
            {
              name: 'react-vendor',
              test: /node_modules[\\/](?:react|react-dom|scheduler)(?:[\\/]|$)/,
            },
            {
              name: 'xyflow-vendor',
              test: /node_modules[\\/]@xyflow[\\/]/,
            },
            {
              name: 'dagre-vendor',
              test: /node_modules[\\/]@dagrejs[\\/]/,
            },
            {
              name: 'json-viewer-vendor',
              test: /node_modules[\\/]@microlink[\\/]/,
            },
            {
              name: 'vendor',
              test: /node_modules[\\/]/,
              maxSize: 400_000, // Byte target stays below Vite's default 500 kB warning threshold.
            },
          ],
        },
      },
    },
  },
});
