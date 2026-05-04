import { withSerwist } from "@serwist/turbopack";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  serverExternalPackages: ["pouchdb-browser", "pouchdb-find"],

  // Turbopack (next dev) — alias PouchDB to empty module on server
  turbopack: {
    resolveAlias: {
      "pouchdb-browser": {
        browser: "pouchdb-browser",
      },
    },
  },

  // Webpack (next build) — null the import on server
  webpack: (config, { isServer }) => {
    if (isServer) {
      config.resolve.alias = {
        ...config.resolve.alias,
        "pouchdb-browser": false,
        "pouchdb-find": false,
      };
    }
    return config;
  },
};

export default withSerwist(nextConfig);
