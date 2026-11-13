/* eslint-disable no-console */
/**
 * Hybrid Cloud Firestore Schema & DBML ERD Exporter.
 *
 * Combines:
 *   1. Static domain entities & relationships from NestJS (`schema-metadata.ts`)
 *   2. Live Cloud Firestore introspection (`listCollections`, sampled docs, recursive subcollections)
 *
 * Outputs:
 *   - `<outDir>/firestore-erd.dbml`   (ready to paste/import into https://dbdiagram.io)
 *   - `<outDir>/firestore.schema.json` (JSON Schema Draft 2020-12 + Firestore metadata)
 *
 * Usage:
 *   npm run export:schema
 *   npm run export:schema -- --sample=100
 *   npm run export:schema -- --static-only
 *   npm run export:schema -- --out-dir=../docs
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'fs';
import { dirname, resolve } from 'path';
import {
  DbmlScalarType,
  JsonSchemaType,
  STATIC_COLLECTIONS,
  STATIC_RELATIONS,
  StaticCollectionDef,
  StaticRelationDef,
} from './schema-metadata';

type SourceProvenance = 'code+firestore' | 'code-only' | 'firestore-only';

interface MergedField {
  name: string;
  dbmlType: DbmlScalarType;
  jsonType: JsonSchemaType;
  jsonItemTypes?: Set<JsonSchemaType>;
  format?: string;
  enumValues?: readonly string[];
  required: boolean;
  pk: boolean;
  note?: string;
  source: SourceProvenance;
  seenInDocs: number;
}

interface MergedCollection {
  tableName: string;
  firestorePath: string;
  group: StaticCollectionDef['group'] | 'Live-Only / Legacy';
  docIdField: string;
  parentTable?: string;
  parentFkField?: string;
  note: string;
  source: SourceProvenance;
  sampledDocs: number;
  fields: Map<string, MergedField>;
}

interface LiveCollectionStats {
  firestorePath: string;
  tableName: string;
  parentTable?: string;
  parentFirestorePath?: string;
  sampledDocs: number;
  fields: Map<string, LiveFieldAccumulator>;
  docRefTargets: Map<string, Set<string>>;
}

interface LiveFieldAccumulator {
  name: string;
  seenInDocs: number;
  types: Set<string>;
  arrayItemTypes: Set<JsonSchemaType>;
  maxStringLen: number;
  allIntegers: boolean;
  hasNumber: boolean;
  hasIsoDate: boolean;
  hasUrl: boolean;
}

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?$/;

function parseArgs(argv: string[]): {
  staticOnly: boolean;
  sampleLimit: number;
  outDir: string;
} {
  let staticOnly = false;
  let sampleLimit = 50;
  let outDir = resolve(process.cwd(), '..', 'docs');

  for (const arg of argv) {
    if (arg === '--static-only') {
      staticOnly = true;
    } else if (arg.startsWith('--sample=')) {
      const parsed = Number(arg.slice('--sample='.length));
      if (Number.isFinite(parsed) && parsed > 0) {
        sampleLimit = Math.floor(parsed);
      }
    } else if (arg.startsWith('--out-dir=')) {
      outDir = resolve(process.cwd(), arg.slice('--out-dir='.length));
    }
  }

  return { staticOnly, sampleLimit, outDir };
}

function findServiceAccountPath(): string | null {
  const configured = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (configured) {
    const candidate = resolve(process.cwd(), configured);
    if (existsSync(candidate)) {
      return candidate;
    }
  }

  const defaultCandidate = resolve(process.cwd(), 'firebase-service-account.json');
  if (existsSync(defaultCandidate)) {
    return defaultCandidate;
  }

  const files = readdirSync(process.cwd());
  const matched = files.find((f) => f.endsWith('.json') && f.includes('firebase-adminsdk'));
  return matched ? resolve(process.cwd(), matched) : null;
}

function initFirestore(): admin.firestore.Firestore | null {
  const saPath = findServiceAccountPath();
  if (!saPath) {
    return null;
  }
  if (admin.apps.length === 0) {
    const serviceAccount = JSON.parse(readFileSync(saPath, 'utf8')) as admin.ServiceAccount;
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  }
  return admin.firestore();
}

function toTableName(firestorePath: string): string {
  // Turn "posts/{doc}/views" into "posts_views"
  return firestorePath
    .split('/')
    .filter((_, idx) => idx % 2 === 0)
    .join('_')
    .replace(/[^a-zA-Z0-9_]/g, '_');
}

function isTimestampValue(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false;
  }
  if (value instanceof admin.firestore.Timestamp) {
    return true;
  }
  const maybeTs = value as { toDate?: unknown; _seconds?: unknown; seconds?: unknown };
  return (
    typeof maybeTs.toDate === 'function' ||
    typeof maybeTs._seconds === 'number' ||
    typeof maybeTs.seconds === 'number'
  );
}

function isDocumentReference(value: unknown): value is admin.firestore.DocumentReference {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const ref = value as { path?: unknown; firestore?: unknown; id?: unknown };
  return (
    value instanceof admin.firestore.DocumentReference ||
    (typeof ref.path === 'string' && typeof ref.id === 'string' && ref.firestore !== undefined)
  );
}

function isGeoPoint(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false;
  }
  if (value instanceof admin.firestore.GeoPoint) {
    return true;
  }
  const maybeGeo = value as { latitude?: unknown; longitude?: unknown };
  return (
    typeof maybeGeo.latitude === 'number' &&
    typeof maybeGeo.longitude === 'number' &&
    Object.keys(value).length === 2
  );
}

function classifyJsonScalar(value: unknown): JsonSchemaType | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? 'integer' : 'number';
  }
  if (typeof value === 'string' || isTimestampValue(value) || isDocumentReference(value)) {
    return 'string';
  }
  if (Array.isArray(value)) {
    return 'array';
  }
  return 'object';
}

function recordLiveValue(
  stats: LiveCollectionStats,
  fieldName: string,
  value: unknown,
): void {
  let acc = stats.fields.get(fieldName);
  if (!acc) {
    acc = {
      name: fieldName,
      seenInDocs: 0,
      types: new Set<string>(),
      arrayItemTypes: new Set<JsonSchemaType>(),
      maxStringLen: 0,
      allIntegers: true,
      hasNumber: false,
      hasIsoDate: false,
      hasUrl: false,
    };
    stats.fields.set(fieldName, acc);
  }
  acc.seenInDocs += 1;

  if (value === null || value === undefined) {
    acc.types.add('null');
    return;
  }
  if (isTimestampValue(value)) {
    acc.types.add('timestamp');
    return;
  }
  if (isDocumentReference(value)) {
    acc.types.add('reference');
    const targetCol = value.parent.id;
    const targets = stats.docRefTargets.get(fieldName) ?? new Set<string>();
    targets.add(targetCol);
    stats.docRefTargets.set(fieldName, targets);
    return;
  }
  if (isGeoPoint(value)) {
    acc.types.add('object');
    return;
  }
  if (Array.isArray(value)) {
    acc.types.add('array');
    for (const item of value) {
      const itemType = classifyJsonScalar(item);
      if (itemType) {
        acc.arrayItemTypes.add(itemType);
      }
    }
    return;
  }
  if (typeof value === 'boolean') {
    acc.types.add('boolean');
    return;
  }
  if (typeof value === 'number') {
    acc.types.add('number');
    acc.hasNumber = true;
    if (!Number.isInteger(value)) {
      acc.allIntegers = false;
    }
    return;
  }
  if (typeof value === 'string') {
    acc.types.add('string');
    if (value.length > acc.maxStringLen) {
      acc.maxStringLen = value.length;
    }
    if (ISO_DATE_RE.test(value)) {
      acc.hasIsoDate = true;
    }
    if (value.startsWith('http://') || value.startsWith('https://')) {
      acc.hasUrl = true;
    }
    return;
  }
  if (typeof value === 'object') {
    acc.types.add('object');
  }
}

async function scanCollection(
  colRef: admin.firestore.CollectionReference,
  firestorePath: string,
  sampleLimit: number,
  liveMap: Map<string, LiveCollectionStats>,
  parentTable?: string,
  parentFirestorePath?: string,
): Promise<void> {
  const tableName = toTableName(firestorePath);
  let stats = liveMap.get(tableName);
  if (!stats) {
    stats = {
      firestorePath,
      tableName,
      parentTable,
      parentFirestorePath,
      sampledDocs: 0,
      fields: new Map(),
      docRefTargets: new Map(),
    };
    liveMap.set(tableName, stats);
  }

  const snap = await colRef.limit(sampleLimit).get();
  stats.sampledDocs += snap.size;

  for (const doc of snap.docs) {
    const data = doc.data();
    for (const [key, value] of Object.entries(data)) {
      recordLiveValue(stats, key, value);
    }

    const subcols = await doc.ref.listCollections();
    for (const subcol of subcols) {
      const subPath = `${firestorePath}/{id}/${subcol.id}`;
      await scanCollection(
        subcol,
        subPath,
        Math.min(sampleLimit, 20),
        liveMap,
        tableName,
        firestorePath,
      );
    }
  }
}

async function introspectLiveFirestore(
  db: admin.firestore.Firestore,
  sampleLimit: number,
): Promise<Map<string, LiveCollectionStats>> {
  const liveMap = new Map<string, LiveCollectionStats>();
  const rootCols = await db.listCollections();

  for (const col of rootCols) {
    await scanCollection(col, col.id, sampleLimit, liveMap);
  }
  return liveMap;
}

function inferTypesFromLive(acc: LiveFieldAccumulator): {
  dbmlType: DbmlScalarType;
  jsonType: JsonSchemaType;
  format?: string;
} {
  const nonNull = [...acc.types].filter((t) => t !== 'null');
  if (nonNull.includes('array')) {
    return { dbmlType: 'jsonb', jsonType: 'array' };
  }
  if (nonNull.includes('object')) {
    return { dbmlType: 'jsonb', jsonType: 'object' };
  }
  if (nonNull.includes('timestamp') || (nonNull.length === 1 && nonNull[0] === 'string' && acc.hasIsoDate)) {
    return { dbmlType: 'timestamp', jsonType: 'string', format: 'date-time' };
  }
  if (nonNull.includes('boolean') && nonNull.length === 1) {
    return { dbmlType: 'boolean', jsonType: 'boolean' };
  }
  if (nonNull.includes('number') && nonNull.length === 1) {
    return acc.allIntegers
      ? { dbmlType: 'int', jsonType: 'integer' }
      : { dbmlType: 'float', jsonType: 'number' };
  }
  if (nonNull.includes('reference')) {
    return { dbmlType: 'varchar', jsonType: 'string' };
  }
  if (acc.hasUrl || acc.maxStringLen > 120) {
    return { dbmlType: 'text', jsonType: 'string', format: acc.hasUrl ? 'uri' : undefined };
  }
  return { dbmlType: 'varchar', jsonType: 'string' };
}

function mergeSchemas(
  liveMap: Map<string, LiveCollectionStats>,
): {
  collections: MergedCollection[];
  relations: StaticRelationDef[];
} {
  const mergedMap = new Map<string, MergedCollection>();

  // 1. Seed with static codebase definitions
  for (const def of STATIC_COLLECTIONS) {
    const live = liveMap.get(def.tableName);
    const colSource: SourceProvenance = live ? 'code+firestore' : 'code-only';
    const fields = new Map<string, MergedField>();

    for (const f of def.fields) {
      const liveField = live?.fields.get(f.name);
      const fieldSource: SourceProvenance = liveField
        ? 'code+firestore'
        : f.pk
          ? colSource
          : 'code-only';
      fields.set(f.name, {
        name: f.name,
        dbmlType: f.dbmlType,
        jsonType: f.jsonType,
        jsonItemTypes: liveField?.arrayItemTypes.size ? liveField.arrayItemTypes : undefined,
        format: f.format,
        enumValues: f.enumValues,
        required: Boolean(f.required),
        pk: Boolean(f.pk),
        note: f.note,
        source: fieldSource,
        seenInDocs: liveField?.seenInDocs ?? 0,
      });
    }

    // Append any live-only fields found in Firestore documents (e.g., legacy fields)
    if (live) {
      for (const [fieldName, acc] of live.fields.entries()) {
        if (!fields.has(fieldName)) {
          const inferred = inferTypesFromLive(acc);
          fields.set(fieldName, {
            name: fieldName,
            dbmlType: inferred.dbmlType,
            jsonType: inferred.jsonType,
            jsonItemTypes: acc.arrayItemTypes.size ? acc.arrayItemTypes : undefined,
            format: inferred.format,
            required: live.sampledDocs > 0 && acc.seenInDocs === live.sampledDocs,
            pk: false,
            note: 'Discovered in live Firestore (not in TS entity)',
            source: 'firestore-only',
            seenInDocs: acc.seenInDocs,
          });
        }
      }
    }

    mergedMap.set(def.tableName, {
      tableName: def.tableName,
      firestorePath: def.firestorePath,
      group: def.group,
      docIdField: def.docIdField,
      parentTable: def.parentTable,
      parentFkField: def.parentFkField,
      note: def.note,
      source: colSource,
      sampledDocs: live?.sampledDocs ?? 0,
      fields,
    });
  }

  // 2. Add any live-only collections (e.g., legacy collections like notifications/settings/config)
  for (const [tableName, live] of liveMap.entries()) {
    if (mergedMap.has(tableName)) {
      continue;
    }
    const fields = new Map<string, MergedField>();
    fields.set('id', {
      name: 'id',
      dbmlType: 'varchar',
      jsonType: 'string',
      required: true,
      pk: true,
      note: 'Firestore Document ID',
      source: 'firestore-only',
      seenInDocs: live.sampledDocs,
    });

    for (const [fieldName, acc] of live.fields.entries()) {
      const inferred = inferTypesFromLive(acc);
      fields.set(fieldName, {
        name: fieldName,
        dbmlType: inferred.dbmlType,
        jsonType: inferred.jsonType,
        jsonItemTypes: acc.arrayItemTypes.size ? acc.arrayItemTypes : undefined,
        format: inferred.format,
        required: live.sampledDocs > 0 && acc.seenInDocs === live.sampledDocs && !acc.types.has('null'),
        pk: false,
        source: 'firestore-only',
        seenInDocs: acc.seenInDocs,
      });
    }

    const parentCol = live.parentTable ? mergedMap.get(live.parentTable) : undefined;
    mergedMap.set(tableName, {
      tableName,
      firestorePath: live.firestorePath,
      group: 'Live-Only / Legacy',
      docIdField: 'id',
      parentTable: live.parentTable,
      parentFkField: parentCol?.docIdField,
      note: 'Discovered in live Firestore (not defined in server/src entities).',
      source: 'firestore-only',
      sampledDocs: live.sampledDocs,
      fields,
    });
  }

  // 3. Collect canonical relations + infer relations for live-only collections/fields
  const relations: StaticRelationDef[] = [...STATIC_RELATIONS];
  const seenRelKeys = new Set(
    relations.map((r) => `${r.fromTable}.${r.fromColumn}->${r.toTable}.${r.toColumn}`),
  );

  const pushRelation = (rel: StaticRelationDef) => {
    const key = `${rel.fromTable}.${rel.fromColumn}->${rel.toTable}.${rel.toColumn}`;
    if (!seenRelKeys.has(key)) {
      seenRelKeys.add(key);
      relations.push(rel);
    }
  };

  const heuristicTargets: Record<string, { table: string; col: string }> = {
    uid: { table: 'users', col: 'uid' },
    userId: { table: 'users', col: 'uid' },
    senderId: { table: 'users', col: 'uid' },
    receiverId: { table: 'users', col: 'uid' },
    actorUid: { table: 'users', col: 'uid' },
    viewerId: { table: 'users', col: 'uid' },
    reactorId: { table: 'users', col: 'uid' },
    momentId: { table: 'posts', col: 'momentId' },
    postId: { table: 'posts', col: 'momentId' },
    frameId: { table: 'frames', col: 'frameId' },
    groupId: { table: 'chatGroups', col: 'groupId' },
    packageId: { table: 'topupPackages', col: 'packageId' },
    questId: { table: 'dailyQuests', col: 'questId' },
  };

  for (const [tableName, live] of liveMap.entries()) {
    const col = mergedMap.get(tableName);
    if (!col) {
      continue;
    }
    if (col.parentTable && col.parentFkField) {
      pushRelation({
        fromTable: tableName,
        fromColumn: col.parentFkField,
        toTable: col.parentTable,
        toColumn: col.parentFkField,
        cardinality: '>',
        deleteCascade: true,
        note: 'Subcollection parent',
      });
    }

    for (const [fieldName, targets] of live.docRefTargets.entries()) {
      for (const targetColName of targets) {
        const targetTable = mergedMap.get(toTableName(targetColName));
        if (targetTable) {
          pushRelation({
            fromTable: tableName,
            fromColumn: fieldName,
            toTable: targetTable.tableName,
            toColumn: targetTable.docIdField,
            cardinality: '>',
            note: 'Firestore DocumentReference',
          });
        }
      }
    }

    if (col.source === 'firestore-only') {
      for (const fieldName of col.fields.keys()) {
        const match = heuristicTargets[fieldName];
        if (match && mergedMap.has(match.table)) {
          pushRelation({
            fromTable: tableName,
            fromColumn: fieldName,
            toTable: match.table,
            toColumn: match.col,
            cardinality: '>',
            note: 'Inferred from field name',
          });
        }
      }
    }
  }

  return {
    collections: [...mergedMap.values()],
    relations,
  };
}

function escapeDbmlNote(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\r?\n/g, ' ').trim();
}

function toEnumName(tableName: string, fieldName: string): string {
  return `${tableName}_${fieldName}`
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .toLowerCase();
}

function generateDbml(
  collections: MergedCollection[],
  relations: StaticRelationDef[],
  modeLabel: string,
): string {
  const lines: string[] = [];
  lines.push(`Project SnapgetFirestore {`);
  lines.push(`  database_type: 'Cloud Firestore (NoSQL)'`);
  lines.push(
    `  Note: '${escapeDbmlNote(
      `Auto-generated Snapget Firestore ERD (${modeLabel}) on ${new Date().toISOString()}. Import into https://dbdiagram.io`,
    )}'`,
  );
  lines.push(`}`);
  lines.push('');

  // Enums
  const enumSet = new Map<string, readonly string[]>();
  for (const col of collections) {
    for (const field of col.fields.values()) {
      if (field.enumValues && field.enumValues.length > 0) {
        enumSet.set(toEnumName(col.tableName, field.name), field.enumValues);
      }
    }
  }
  for (const [enumName, values] of enumSet.entries()) {
    lines.push(`Enum ${enumName} {`);
    for (const v of values) {
      lines.push(`  ${v}`);
    }
    lines.push(`}`);
    lines.push('');
  }

  // Tables
  const tableFieldSet = new Map<string, Set<string>>();
  for (const col of collections) {
    const colFieldNames = new Set<string>();
    tableFieldSet.set(col.tableName, colFieldNames);

    const sampleInfo =
      col.sampledDocs > 0 ? `sampled ${col.sampledDocs} docs` : 'no live docs sampled';
    lines.push(`Table ${col.tableName} {`);

    // Ensure subcollections expose their virtual parent FK column for DBML relationships
    if (col.parentTable && col.parentFkField && !col.fields.has(col.parentFkField)) {
      colFieldNames.add(col.parentFkField);
      lines.push(
        `  ${col.parentFkField} varchar [not null, note: 'Parent document ID (${escapeDbmlNote(
          col.firestorePath,
        )})']`,
      );
    }

    for (const field of col.fields.values()) {
      colFieldNames.add(field.name);
      const attrs: string[] = [];
      if (field.pk) {
        attrs.push('pk');
      }
      if (field.required) {
        attrs.push('not null');
      }
      const noteParts: string[] = [];
      if (field.note) {
        noteParts.push(field.note);
      }
      if (field.source === 'firestore-only') {
        noteParts.push('[firestore-only]');
      } else if (field.source === 'code-only' && col.source === 'code+firestore') {
        noteParts.push('[optional/unseen in sample]');
      }
      if (noteParts.length > 0) {
        attrs.push(`note: '${escapeDbmlNote(noteParts.join(' · '))}'`);
      }

      const typeToken =
        field.enumValues && field.enumValues.length > 0
          ? toEnumName(col.tableName, field.name)
          : field.dbmlType;
      const attrSuffix = attrs.length > 0 ? ` [${attrs.join(', ')}]` : '';
      lines.push(`  ${field.name} ${typeToken}${attrSuffix}`);
    }

    lines.push(
      `  Note: '${escapeDbmlNote(
        `Firestore path: ${col.firestorePath} (${col.source}, ${sampleInfo}). ${col.note}`,
      )}'`,
    );
    lines.push(`}`);
    lines.push('');
  }

  // TableGroups
  const groups = new Map<string, string[]>();
  for (const col of collections) {
    const list = groups.get(col.group) ?? [];
    list.push(col.tableName);
    groups.set(col.group, list);
  }
  for (const [groupName, tableNames] of groups.entries()) {
    const safeGroupName = groupName.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '');
    lines.push(`TableGroup ${safeGroupName} {`);
    for (const t of tableNames) {
      lines.push(`  ${t}`);
    }
    lines.push(`}`);
    lines.push('');
  }

  // Relationships
  for (const rel of relations) {
    const fromFields = tableFieldSet.get(rel.fromTable);
    const toFields = tableFieldSet.get(rel.toTable);
    if (!fromFields?.has(rel.fromColumn) || !toFields?.has(rel.toColumn)) {
      continue;
    }
    const opts: string[] = [];
    if (rel.deleteCascade) {
      opts.push('delete: cascade');
    }
    const optSuffix = opts.length > 0 ? ` [${opts.join(', ')}]` : '';
    const comment = rel.note ? ` // ${rel.note}` : '';
    lines.push(
      `Ref: ${rel.fromTable}.${rel.fromColumn} ${rel.cardinality} ${rel.toTable}.${rel.toColumn}${optSuffix}${comment}`,
    );
  }
  lines.push('');

  return lines.join('\n');
}

function generateJsonSchema(
  collections: MergedCollection[],
  relations: StaticRelationDef[],
  modeLabel: string,
): Record<string, unknown> {
  const defs: Record<string, unknown> = {};
  const rootProperties: Record<string, unknown> = {};

  for (const col of collections) {
    const properties: Record<string, unknown> = {};
    const required: string[] = [];

    for (const field of col.fields.values()) {
      const prop: Record<string, unknown> = {
        type: field.jsonType,
      };
      if (field.format) {
        prop.format = field.format;
      }
      if (field.enumValues && field.enumValues.length > 0) {
        prop.enum = [...field.enumValues];
      }
      if (field.jsonType === 'array' && field.jsonItemTypes && field.jsonItemTypes.size > 0) {
        const itemsArr = [...field.jsonItemTypes];
        prop.items = itemsArr.length === 1 ? { type: itemsArr[0] } : { type: itemsArr };
      }
      if (field.note) {
        prop.description = field.note;
      }
      prop['x-source'] = field.source;
      if (col.sampledDocs > 0) {
        prop['x-seenInDocs'] = field.seenInDocs;
      }

      properties[field.name] = prop;
      if (field.required) {
        required.push(field.name);
      }
    }

    const colRelations = relations
      .filter((r) => r.fromTable === col.tableName)
      .map((r) => ({
        field: r.fromColumn,
        targetCollection: r.toTable,
        targetField: r.toColumn,
        ...(r.note ? { note: r.note } : {}),
      }));

    const subcollections = collections
      .filter((c) => c.parentTable === col.tableName)
      .map((c) => c.tableName);

    defs[col.tableName] = {
      type: 'object',
      title: col.tableName,
      description: col.note,
      required,
      properties,
      'x-firestore': {
        path: col.firestorePath,
        docIdField: col.docIdField,
        group: col.group,
        source: col.source,
        sampledDocs: col.sampledDocs,
        ...(col.parentTable ? { parentCollection: col.parentTable } : {}),
        ...(subcollections.length > 0 ? { subcollections } : {}),
        ...(colRelations.length > 0 ? { relations: colRelations } : {}),
      },
    };

    if (!col.parentTable) {
      rootProperties[col.tableName] = {
        type: 'array',
        description: `Collection "${col.firestorePath}"`,
        items: { $ref: `#/$defs/${col.tableName}` },
      };
    }
  }

  return {
    $schema: 'https://json-schema.org/draft/2020-12/schema',
    $id: 'https://snapget-d8693.web.app/schemas/firestore.schema.json',
    title: 'Snapget Cloud Firestore Database Schema',
    description: `Generated (${modeLabel}) on ${new Date().toISOString()} from NestJS entities and Cloud Firestore introspection.`,
    type: 'object',
    properties: rootProperties,
    $defs: defs,
  };
}

async function main(): Promise<void> {
  const { staticOnly, sampleLimit, outDir } = parseArgs(process.argv.slice(2));
  let liveMap = new Map<string, LiveCollectionStats>();
  let modeLabel = 'code-only';

  if (!staticOnly) {
    const db = initFirestore();
    if (db) {
      try {
        console.log(`Introspecting live Cloud Firestore (sample <= ${sampleLimit} docs/collection)...`);
        liveMap = await introspectLiveFirestore(db, sampleLimit);
        modeLabel = 'hybrid: code + live Firestore';
        console.log(`Scanned ${liveMap.size} live collections/subcollections.`);
      } catch (err) {
        console.warn(
          `Could not reach live Cloud Firestore (${(err as Error).message}). Falling back to static codebase schema.`,
        );
      }
    } else {
      console.warn('No Firebase service account JSON found. Falling back to static codebase schema.');
    }
  }

  const { collections, relations } = mergeSchemas(liveMap);
  const dbmlContent = generateDbml(collections, relations, modeLabel);
  const jsonSchema = generateJsonSchema(collections, relations, modeLabel);

  mkdirSync(outDir, { recursive: true });
  const dbmlPath = resolve(outDir, 'firestore-erd.dbml');
  const jsonPath = resolve(outDir, 'firestore.schema.json');

  mkdirSync(dirname(dbmlPath), { recursive: true });
  writeFileSync(dbmlPath, dbmlContent, 'utf8');
  writeFileSync(jsonPath, `${JSON.stringify(jsonSchema, null, 2)}\n`, 'utf8');

  console.log(`Exported DBML ERD   -> ${dbmlPath}`);
  console.log(`Exported JSON Schema -> ${jsonPath}`);
}

main().catch((err) => {
  console.error('Export failed:', err);
  process.exit(1);
});

