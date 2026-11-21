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
