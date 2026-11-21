import { readdirSync, readFileSync } from "node:fs";

const expectedLiveVersions = [
  "20261027232300",
  "20261028002834",
  "20261028002944",
  "20261028102747",
  "20261030180810",
  "20261030202848",
  "20261030230016",
  "20261030231014",
  "20261030231529",
  "20261031232509",
  "20261101121639",
  "20261101121711",
  "20261101191601",
  "20261101191636",
  "20261101191840",
  "20261101192215",
  "20261101192359",
  "20261101194034",
  "20261102200232",
  "20261102200923",
  "20261102201210",
  "20261102201315",
  "20261105020850",
  "20261105020932",
  "20261105021142",
  "20261107070030",
  "20261107070158",
  "20261107070320",
  "20261107071201",
  "20261107071316",
  "20261107071614",
  "20261107071850",
  "20261107072031",
  "20261107084008",
  "20261108025145",
  "20261108030703",
  "20261108031032",
  "20261108031111",
  "20261108032148",
  "20261108032308",
  "20261108230111",
  "20261108230316",
  "20261116162504",
  "20261116162608",
  "20261116162721",
  "20261120082626",
  "20261120083422",
  "20261120180335",
  "20261121153907",
  "20261121154050",
  "20261121154406",
  "20261121161222",
  "20261121162206"
];
const migrationFiles = readdirSync("supabase/migrations").filter(name => /^\d{8,14}_.+\.sql$/.test(name));
const actualVersions = migrationFiles.map(name => name.match(/^\d{8,14}/)[0]);
const actualSet = new Set(actualVersions);
const duplicates = actualVersions.filter((version,index) => actualVersions.indexOf(version) !== index);
const missingFiles = expectedLiveVersions.filter(version => !actualSet.has(version));
const manifest = readFileSync("supabase/MIGRATION-RECONCILIATION.md","utf8");
const missingManifestRows = expectedLiveVersions.filter(version => !manifest.includes("| `"+version+"` |"));
if (duplicates.length || missingFiles.length || missingManifestRows.length) {
  console.error(JSON.stringify({duplicates,missingFiles,missingManifestRows},null,2));
  process.exit(1);
}
console.log(`Verified ${expectedLiveVersions.length} live migration versions have unique formal source files and manifest entries.`);

const orderedFiles = [...migrationFiles].sort();
const createdAt = new Map();
const sqlByFile = new Map();
for (const file of orderedFiles) {
  const sql = readFileSync(`supabase/migrations/${file}`, "utf8")
    .replace(/--[^\n]*/g, "")
    .replace(/\/\*[\s\S]*?\*\//g, "");
  sqlByFile.set(file, sql);
  for (const match of sql.matchAll(/create\s+table\s+(?:if\s+not\s+exists\s+)?((?:public|private)\.[a-z_][a-z0-9_]*)/ig)) {
    const table = match[1].toLowerCase();
    if (!createdAt.has(table)) createdAt.set(table, { file, index: match.index });
  }
}

const sqlErrors = [];
for (const file of orderedFiles) {
  const sql = sqlByFile.get(file);
  const dollarTags = sql.match(/\$[A-Za-z_][A-Za-z0-9_]*\$|\$\$/g) || [];
  const tagCounts = new Map();
  for (const tag of dollarTags) tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
  for (const [tag, count] of tagCounts) {
    if (count % 2 !== 0) sqlErrors.push({ file, issue: `Unbalanced dollar quote ${tag} (count ${count})` });
  }

  const referencePattern = /\b(?:alter\s+table|on(?:\s+table)?|from|join|into|update|delete\s+from|references)\s+(public|private)\.([a-z_][a-z0-9_]*)/ig;
  for (const match of sql.matchAll(referencePattern)) {
    const table = `${match[1].toLowerCase()}.${match[2].toLowerCase()}`;
    const creation = createdAt.get(table);
    if (!creation) continue; // Supabase-managed or pre-existing object; validated separately.
    if (creation.file.localeCompare(file) > 0 ||
        (creation.file === file && creation.index > match.index)) {
      sqlErrors.push({ file, issue: `${table} is referenced before it is created in ${creation.file}` });
    }
  }
}

if (sqlErrors.length) {
  console.error(JSON.stringify({ sqlErrors }, null, 2));
  process.exit(1);
}
console.log(`Verified SQL dollar-quote balance and in-order references across ${orderedFiles.length} migration files.`);
