package io.shockah.func;

@FunctionalInterface
public interface Func1<T1, R> {
	public R call(T1 t1);
}