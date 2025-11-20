// load_test.js
const http = require('http');

// --- CONFIGURATION ---
// (I have pre-filled these based on your provided logs)

const VALID_SESSION_ID = "ea7db19d-cbae-4164-953f-ca3c75669087"; 
const VALID_NONCE = "f81f9a1b-d4d1-450b-8cb0-4dd733e9e740";

// This is the token from your config snippet
const JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdHVkZW50SWQiOiI2NGY4Y2FlYy1iYmZjLTQ1YzYtYWE4My02MTJhZDUzYmUzNjUiLCJkZXZpY2VJZCI6ImRldi03ZmQyODU1OS0zODI5LTQwNjktYTg3NC0wZWRmZDgxZTYxMWEiLCJpYXQiOjE3NjM1NTg3MjEsImV4cCI6MTc2NjE1MDcyMX0.384xSL7qBLKtG5UNY38JMwV40s2G9K5I0wy83NMp2jI";

const DEVICE_ID = "dev-7fd28559-3829-4069-a874-0edfd81e611a";

const TOTAL_REQUESTS = 100; 

// --- LOAD TEST LOGIC (Native HTTP) ---

function sendRequest(index) {
    return new Promise((resolve) => {
        const start = Date.now();
        const uniqueId = `load-test-${Date.now()}-${index}`;

        const payload = JSON.stringify({
            events: [{
                attendance: {
                    idempotency_key: uniqueId,
                    sess: VALID_SESSION_ID,
                    qrNonce: VALID_NONCE,
                    device_id: DEVICE_ID,
                    lat: 26.250222,
                    lon: 78.169803,
                    ts_client: new Date().toISOString()
                },
                student_sig: "TEST_SIG"
            }]
        });

        const options = {
            hostname: '127.0.0.1', // Using IP avoids localhost DNS issues
            port: 4000,
            path: '/api/attendance/batch',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${JWT_TOKEN}`,
                'Content-Length': Buffer.byteLength(payload)
            }
        };

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                const end = Date.now();
                try {
                    const json = JSON.parse(data);
                    // Success if HTTP 200 AND the internal status is 'ok'
                    const isSuccess = res.statusCode === 200 && json.results?.[0]?.status === 'ok';
                    resolve({ 
                        status: res.statusCode, 
                        appStatus: json.results?.[0]?.status || 'unknown',
                        time: end - start,
                        success: isSuccess
                    });
                } catch (e) {
                    resolve({ status: res.statusCode, appStatus: 'parse_error', time: end - start, success: false });
                }
            });
        });

        req.on('error', (e) => {
            console.error(`Req ${index} failed:`, e.message);
            resolve({ status: 'conn_err', time: 0, success: false, error: e.message });
        });

        req.write(payload);
        req.end();
    });
}

async function runTest() {
    console.log(`\n--- STARTING LOAD TEST ---`);
    console.log(`Target: http://127.0.0.1:4000/api/attendance/batch`);
    console.log(`Simulating: ${TOTAL_REQUESTS} concurrent students`);
    console.log(`...sending requests...`);

    const promises = [];
    for(let i=0; i<TOTAL_REQUESTS; i++) {
        promises.push(sendRequest(i));
    }

    const results = await Promise.all(promises);
    
    const totalTime = results.reduce((acc, curr) => acc + curr.time, 0);
    const avgTime = totalTime / TOTAL_REQUESTS;
    const successCount = results.filter(r => r.success).length;

    console.log("\n--- RESULTS ---");
    console.log(`Total Requests: ${TOTAL_REQUESTS}`);
    console.log(`Successful DB Writes: ${successCount}`);
    console.log(`Failed Requests: ${TOTAL_REQUESTS - successCount}`);
    console.log(`Average Response Time: ${avgTime.toFixed(2)} ms`);
    
    if (successCount !== TOTAL_REQUESTS) {
        console.log("\nError Samples:");
        console.log(results.filter(r => !r.success).slice(0, 3));
    }
}

runTest();