#!/bin/bash
# Show all limits for the current process: ulimits and cgroup limits
# Supports both cgroups v1 and v2 with minimal dependencies

set -e

echo "==================================="
echo "Process Limits Report"
echo "PID: $$"
echo "==================================="
echo

# Function to read cgroup file safely
read_cgroup_file() {
    local file="$1"
    if [ -f "$file" ] && [ -r "$file" ]; then
        cat "$file" 2>/dev/null || echo "N/A"
    else
        echo "N/A"
    fi
}

# Function to format large numbers
format_value() {
    local val="$1"
    if [ "$val" = "max" ] || [ "$val" = "unlimited" ]; then
        echo "unlimited"
    elif [ "$val" -eq "$val" ] 2>/dev/null; then
        if [ "$val" -ge 1073741824 ]; then
            local gb=$((val / 1073741824))
            local remainder=$((val % 1073741824))
            local frac=$((remainder * 100 / 1073741824))
            echo "$val (${gb}.${frac}G)"
        elif [ "$val" -ge 1048576 ]; then
            local mb=$((val / 1048576))
            local remainder=$((val % 1048576))
            local frac=$((remainder * 100 / 1048576))
            echo "$val (${mb}.${frac}M)"
        elif [ "$val" -ge 1024 ]; then
            local kb=$((val / 1024))
            local remainder=$((val % 1024))
            local frac=$((remainder * 100 / 1024))
            echo "$val (${kb}.${frac}K)"
        else
            echo "$val"
        fi
    else
        echo "$val"
    fi
}

# Show ulimits
echo "--- ULIMITS (soft / hard) ---"
echo
echo "Core file size:          $(ulimit -Sc) / $(ulimit -Hc)"
echo "Data segment size:       $(ulimit -Sd) / $(ulimit -Hd)"
echo "Scheduling priority:     $(ulimit -Se) / $(ulimit -He)"
echo "File size:               $(ulimit -Sf) / $(ulimit -Hf)"
echo "Pending signals:         $(ulimit -Si) / $(ulimit -Hi)"
echo "Max locked memory:       $(ulimit -Sl) / $(ulimit -Hl)"
echo "Max memory size:         $(ulimit -Sm) / $(ulimit -Hm)"
echo "Open files:              $(ulimit -Sn) / $(ulimit -Hn)"
echo "Pipe size:               $(ulimit -Sp) / $(ulimit -Hp)"
echo "POSIX message queues:    $(ulimit -Sq) / $(ulimit -Hq)"
echo "Real-time priority:      $(ulimit -Sr) / $(ulimit -Hr)"
echo "Stack size:              $(ulimit -Ss) / $(ulimit -Hs)"
echo "CPU time:                $(ulimit -St) / $(ulimit -Ht)"
echo "Max user processes:      $(ulimit -Su) / $(ulimit -Hu)"
echo "Virtual memory:          $(ulimit -Sv) / $(ulimit -Hv)"
echo "File locks:              $(ulimit -Sx) / $(ulimit -Hx)"
echo

# Detect cgroup version and show limits
CGROUP_ROOT="/sys/fs/cgroup"
PROC_CGROUP="/proc/$$/cgroup"

if [ ! -f "$PROC_CGROUP" ]; then
    echo "--- CGROUPS ---"
    echo "Not available (no $PROC_CGROUP)"
    exit 0
fi

# Check cgroup version
if [ -f "$CGROUP_ROOT/cgroup.controllers" ]; then
    CGROUP_VERSION="v2"
else
    CGROUP_VERSION="v1"
fi

echo "--- CGROUPS ($CGROUP_VERSION) ---"
echo

if [ "$CGROUP_VERSION" = "v2" ]; then
    # Cgroups v2
    # Find the cgroup path for this process
    CGROUP_PATH=$(grep '^0::' "$PROC_CGROUP" | cut -d: -f3)
    if [ -z "$CGROUP_PATH" ]; then
        echo "Could not determine cgroup path"
        exit 0
    fi

    CGROUP_DIR="$CGROUP_ROOT$CGROUP_PATH"
    echo "Cgroup path: $CGROUP_PATH"
    echo

    # CPU limits
    echo "CPU:"
    CPU_MAX=$(read_cgroup_file "$CGROUP_DIR/cpu.max")
    CPU_WEIGHT=$(read_cgroup_file "$CGROUP_DIR/cpu.weight")
    if [ "$CPU_MAX" != "N/A" ]; then
        echo "  cpu.max:    $CPU_MAX"
    fi
    if [ "$CPU_WEIGHT" != "N/A" ]; then
        echo "  cpu.weight: $CPU_WEIGHT"
    fi
    echo

    # Memory limits
    echo "Memory:"
    MEM_MAX=$(read_cgroup_file "$CGROUP_DIR/memory.max")
    MEM_HIGH=$(read_cgroup_file "$CGROUP_DIR/memory.high")
    MEM_CURRENT=$(read_cgroup_file "$CGROUP_DIR/memory.current")
    MEM_SWAP_MAX=$(read_cgroup_file "$CGROUP_DIR/memory.swap.max")

    if [ "$MEM_MAX" != "N/A" ]; then
        echo "  memory.max:     $(format_value "$MEM_MAX")"
    fi
    if [ "$MEM_HIGH" != "N/A" ]; then
        echo "  memory.high:    $(format_value "$MEM_HIGH")"
    fi
    if [ "$MEM_CURRENT" != "N/A" ]; then
        echo "  memory.current: $(format_value "$MEM_CURRENT")"
    fi
    if [ "$MEM_SWAP_MAX" != "N/A" ]; then
        echo "  memory.swap.max: $(format_value "$MEM_SWAP_MAX")"
    fi
    echo

    # I/O limits
    echo "I/O:"
    IO_MAX=$(read_cgroup_file "$CGROUP_DIR/io.max")
    if [ "$IO_MAX" != "N/A" ]; then
        echo "  io.max:"
        echo "$IO_MAX" | while read -r line; do
            [ -z "$line" ] || echo "    $line"
        done
    fi
    echo

    # PIDs limit
    echo "PIDs:"
    PIDS_MAX=$(read_cgroup_file "$CGROUP_DIR/pids.max")
    PIDS_CURRENT=$(read_cgroup_file "$CGROUP_DIR/pids.current")
    if [ "$PIDS_MAX" != "N/A" ]; then
        echo "  pids.max:     $PIDS_MAX"
    fi
    if [ "$PIDS_CURRENT" != "N/A" ]; then
        echo "  pids.current: $PIDS_CURRENT"
    fi

else
    # Cgroups v1
    echo "Cgroup controllers:"
    while IFS=: read -r _ controllers path; do
        [ "$controllers" ] || continue
        echo "  $controllers -> $path"
    done < "$PROC_CGROUP"
    echo

    # CPU limits (cpu controller)
    CPU_PATH=$(grep ':cpu,' "$PROC_CGROUP" | cut -d: -f3)
    if [ -n "$CPU_PATH" ]; then
        CPU_DIR="$CGROUP_ROOT/cpu$CPU_PATH"
        echo "CPU (cpu controller):"
        CFS_PERIOD=$(read_cgroup_file "$CPU_DIR/cpu.cfs_period_us")
        CFS_QUOTA=$(read_cgroup_file "$CPU_DIR/cpu.cfs_quota_us")
        CPU_SHARES=$(read_cgroup_file "$CPU_DIR/cpu.shares")

        if [ "$CFS_PERIOD" != "N/A" ]; then
            echo "  cpu.cfs_period_us: $CFS_PERIOD"
        fi
        if [ "$CFS_QUOTA" != "N/A" ]; then
            echo "  cpu.cfs_quota_us:  $CFS_QUOTA"
            if [ "$CFS_QUOTA" != "-1" ] && [ "$CFS_PERIOD" != "N/A" ]; then
                cores=$((CFS_QUOTA / CFS_PERIOD))
                remainder=$((CFS_QUOTA % CFS_PERIOD))
                frac=$((remainder * 100 / CFS_PERIOD))
                echo "  Effective cores:   ${cores}.${frac}"
            fi
        fi
        if [ "$CPU_SHARES" != "N/A" ]; then
            echo "  cpu.shares:        $CPU_SHARES"
        fi
        echo
    fi

    # Memory limits (memory controller)
    MEM_PATH=$(grep ':memory:' "$PROC_CGROUP" | cut -d: -f3)
    if [ -n "$MEM_PATH" ]; then
        MEM_DIR="$CGROUP_ROOT/memory$MEM_PATH"
        echo "Memory (memory controller):"
        MEM_LIMIT=$(read_cgroup_file "$MEM_DIR/memory.limit_in_bytes")
        MEM_USAGE=$(read_cgroup_file "$MEM_DIR/memory.usage_in_bytes")
        MEM_SWAP_LIMIT=$(read_cgroup_file "$MEM_DIR/memory.memsw.limit_in_bytes")

        if [ "$MEM_LIMIT" != "N/A" ]; then
            echo "  memory.limit_in_bytes: $(format_value "$MEM_LIMIT")"
        fi
        if [ "$MEM_USAGE" != "N/A" ]; then
            echo "  memory.usage_in_bytes: $(format_value "$MEM_USAGE")"
        fi
        if [ "$MEM_SWAP_LIMIT" != "N/A" ]; then
            echo "  memory.memsw.limit_in_bytes: $(format_value "$MEM_SWAP_LIMIT")"
        fi
        echo
    fi

    # PIDs limit (pids controller)
    PIDS_PATH=$(grep ':pids:' "$PROC_CGROUP" | cut -d: -f3)
    if [ -n "$PIDS_PATH" ]; then
        PIDS_DIR="$CGROUP_ROOT/pids$PIDS_PATH"
        echo "PIDs (pids controller):"
        PIDS_MAX=$(read_cgroup_file "$PIDS_DIR/pids.max")
        PIDS_CURRENT=$(read_cgroup_file "$PIDS_DIR/pids.current")

        if [ "$PIDS_MAX" != "N/A" ]; then
            echo "  pids.max:     $PIDS_MAX"
        fi
        if [ "$PIDS_CURRENT" != "N/A" ]; then
            echo "  pids.current: $PIDS_CURRENT"
        fi
        echo
    fi

    # I/O limits (blkio controller)
    BLKIO_PATH=$(grep ':blkio:' "$PROC_CGROUP" | cut -d: -f3)
    if [ -n "$BLKIO_PATH" ]; then
        BLKIO_DIR="$CGROUP_ROOT/blkio$BLKIO_PATH"
        echo "I/O (blkio controller):"
        BLKIO_WEIGHT=$(read_cgroup_file "$BLKIO_DIR/blkio.weight")

        if [ "$BLKIO_WEIGHT" != "N/A" ]; then
            echo "  blkio.weight: $BLKIO_WEIGHT"
        fi

        # Check for throttle settings
        THROTTLE_READ=$(read_cgroup_file "$BLKIO_DIR/blkio.throttle.read_bps_device")
        THROTTLE_WRITE=$(read_cgroup_file "$BLKIO_DIR/blkio.throttle.write_bps_device")

        if [ "$THROTTLE_READ" != "N/A" ] && [ -n "$THROTTLE_READ" ]; then
            echo "  blkio.throttle.read_bps_device:"
            echo "$THROTTLE_READ" | while read -r line; do
                [ -z "$line" ] || echo "    $line"
            done
        fi
        if [ "$THROTTLE_WRITE" != "N/A" ] && [ -n "$THROTTLE_WRITE" ]; then
            echo "  blkio.throttle.write_bps_device:"
            echo "$THROTTLE_WRITE" | while read -r line; do
                [ -z "$line" ] || echo "    $line"
            done
        fi
        echo
    fi
fi

echo "==================================="
