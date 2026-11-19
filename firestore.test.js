const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write transport sheets, vehicles, or documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sheets").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("v1").set({
      userId: ALICE_UID,
      plateNumber: "MH12PQ4821",
      vehicleClass: "TRUCK",
      nickname: "Cargo 1",
      ownerName: "Owner",
      rtoOffice: "Pune",
      fuelType: "Diesel",
      chassisMasked: "MAT***90",
      challanCount: 0,
      challanAmount: 0,
      challanDueDate: "",
      rtcPermitExpiry: "",
      insuranceProvider: "ICICI Lombard",
      insurancePremium: 18500,
      insuranceDueDate: "2026-11-22",
      alertsEnabled: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create and partially update sheets, rows, vehicles, and documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sheets").doc("sheet_1").set({
      userId: ALICE_UID,
      name: "November Freight Register",
      category: "FREIGHT",
      syncPlugUrl: "",
      syncEnabled: false,
      columns: ["Date", "Vehicle", "Route", "Bill No", "Freight", "Expense", "Status"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sheets").doc("sheet_1").collection("rows").doc("row_1").set({
      userId: ALICE_UID,
      sheetId: "sheet_1",
      dateStr: "2026-11-19",
      vehicleNo: "MH12PQ4821",
      partyOrRoute: "Pune to Bhiwandi",
      billNo: "LR-408",
      freightAmount: 24500,
      expenseAmount: 8200,
      status: "PAID",
      notes: "Unloaded on time",
      sourceLabel: "Manual Entry",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sheets").doc("sheet_1").collection("rows").doc("row_1").update({
      freightAmount: 25500,
      status: "BILLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_1").set({
      userId: ALICE_UID,
      plateNumber: "MH12PQ4821",
      vehicleClass: "TRUCK",
      nickname: "BharatBenz 2823",
      ownerName: "Rajesh Transport",
      rtoOffice: "MH-12 Pune",
      fuelType: "Diesel",
      chassisMasked: "MEC***4821",
      challanCount: 1,
      challanAmount: 2000,
      challanDueDate: "2026-11-21",
      rtcPermitExpiry: "2027-02-15",
      insuranceProvider: "New India Assurance",
      insurancePremium: 24500,
      insuranceDueDate: "2026-11-22",
      alertsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1").set({
      userId: ALICE_UID,
      title: "Freight Bill LR-408",
      docType: "FREIGHT_INVOICE",
      vehicleNo: "MH12PQ4821",
      partyName: "Apex Steel Traders",
      amount: 25500,
      photoCount: 2,
      summaryNote: "Includes loading slip and weighbridge receipt.",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cross-user isolation and ghost field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("vehicles").doc("bob_v1").set({
      userId: BOB_UID,
      plateNumber: "MH04AB9900",
      vehicleClass: "LONG_VEHICLE",
      nickname: "Trailer 40ft",
      ownerName: "Bob Logistics",
      rtoOffice: "MH-04 Thane",
      fuelType: "Diesel",
      chassisMasked: "MAT***9900",
      challanCount: 0,
      challanAmount: 0,
      challanDueDate: "",
      rtcPermitExpiry: "",
      insuranceProvider: "HDFC Ergo",
      insurancePremium: 31000,
      insuranceDueDate: "2026-12-01",
      alertsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vehicles").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vehicles").doc("bob_v1").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("bad_1").set({
      id: "bad_1",
      userId: ALICE_UID,
      plateNumber: "MH12PQ4821",
      vehicleClass: "TRUCK",
      nickname: "Bad",
      ownerName: "Owner",
      rtoOffice: "Pune",
      fuelType: "Diesel",
      chassisMasked: "",
      challanCount: 0,
      challanAmount: 0,
      challanDueDate: "",
      rtcPermitExpiry: "",
      insuranceProvider: "",
      insurancePremium: 0,
      insuranceDueDate: "",
      alertsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});
