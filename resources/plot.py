import matplotlib.pyplot as plt
import numpy as np

# Data derived from your load_test.js logs
concurrent_users = [
    50, 100, 150, 200, 250, 300, 350, 450, 500, 
    550, 600, 650, 700, 750, 800, 850, 900, 
    1000, 1200, 1500, 1800, 2000
]

avg_response_time_ms = [
    167.50, 290.92, 325.03, 418.04, 479.88, 617.68, 701.84, 939.48, 999.49,
    1084.84, 1205.01, 1289.80, 1358.88, 1475.60, 1620.93, 1593.78, 1789.59,
    1881.13, 2208.04, 2923.66, 3483.66, 3756.68
]

def create_plot():
    plt.figure(figsize=(10, 6))
    
    # Plotting the data
    plt.plot(concurrent_users, avg_response_time_ms, marker='s', linestyle='-', color='#007acc', label='Avg Response Time', linewidth=2, markersize=6)
    
    # Adding specific text annotation for the max load
    plt.annotate(f'{avg_response_time_ms[-1]:.2f}ms', 
                 xy=(concurrent_users[-1], avg_response_time_ms[-1]), 
                 xytext=(concurrent_users[-1]-400, avg_response_time_ms[-1]+100),
                 arrowprops=dict(facecolor='black', shrink=0.05))

    # Formatting the graph
    plt.title('Scalability Test: Load vs. Latency', fontsize=16, fontweight='bold')
    plt.xlabel('Concurrent Users (Simulated)', fontsize=12)
    plt.ylabel('Average Response Time (ms)', fontsize=12)
    
    # Grid and Layout
    plt.grid(True, which='both', linestyle='--', linewidth=0.5)
    plt.legend()
    
    # Set limits to match the data nicely
    plt.xlim(0, 2100)
    plt.ylim(0, 4000)
    
    # Save the plot
    filename = 'scalability_plot.png'
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"Plot saved successfully as {filename}")
    plt.show()

if __name__ == "__main__":
    create_plot()