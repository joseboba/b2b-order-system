# ── Stage 1: Build ───────────────────────────────────────────────────────────
# Installs all dependencies (including devDependencies) and compiles
# TypeScript source into JavaScript under dist/.
FROM node:20-alpine AS builder

WORKDIR /app

# Install all deps first (cached unless package.json / yarn.lock change)
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile

# Compile TypeScript → dist/
COPY . .
RUN yarn build

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Production image: devDependencies and the TypeScript toolchain are excluded,
# reducing the final image size by ~200 MB.
FROM node:20-alpine

ENV NODE_ENV=production
WORKDIR /app

# node:20-alpine ships with a built-in non-root 'node' user
COPY --chown=node:node package.json yarn.lock ./
RUN yarn install --frozen-lockfile --production

# Copy only the compiled output from the build stage
COPY --chown=node:node --from=builder /app/dist ./dist

USER node
EXPOSE 8082
CMD ["node", "dist/main"]
